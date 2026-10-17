const { chromium } = await import(process.env.WHEELAA_PLAYWRIGHT_MODULE || 'playwright');
import http from 'node:http';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const root=new URL('../app/src/main/assets/wheelaa/', import.meta.url).pathname;
const jpeg=Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720"><rect width="1280" height="720" fill="#152b43"/><text x="80" y="120" fill="white" font-size="48">wheelaa browser test fixture</text></svg>');
const touches=[];
const server=http.createServer(async(req,res)=>{
  const route=req.url.split('?')[0];
  if(route==='/api/pair') {res.setHeader('Content-Type','application/json');res.end('{"token":"test-only"}');return;}
  if(route==='/api/frame') {res.setHeader('Content-Type','image/svg+xml');res.end(jpeg);return;}
  if(route==='/api/touch') {let b='';for await(const c of req)b+=c;touches.push(JSON.parse(b));res.end('ok');return;}
  if(route==='/api/disconnect') {res.end('ok');return;}
  const file=route==='/'?'index.html':route.slice(1);
  if(!['index.html','app.js','geometry.js','style.css'].includes(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');
  res.end(await fs.readFile(`${root}/${file}`));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({executablePath:process.env.WHEELAA_CHROMIUM || '/usr/bin/chromium',headless:true,args:['--no-sandbox']});
try {
 const page=await browser.newPage({viewport:{width:1280,height:800}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(`http://127.0.0.1:${server.address().port}`);
 await page.locator('#code').fill('123456');await page.locator('#connect').click();
 await page.waitForFunction(()=>document.querySelector('#waiting').hidden);
 const image=await page.locator('#screen').boundingBox();assert(image);
 await page.mouse.move(image.x+image.width/2,image.y+image.height/2);
 await page.mouse.down();await page.mouse.move(image.x+image.width*.6,image.y+image.height*.6);await page.mouse.up();
 await page.waitForFunction(()=>document.getElementById('status').textContent.includes('已连接'));
 for(let i=0;i<20&&!touches.some(t=>t.action==='up');i++)await page.waitForTimeout(100);
 assert.deepEqual(touches.map(t=>t.action),['down','move','up']);
 assert(Math.abs(touches[0].x-.5)<.01);assert(Math.abs(touches[0].y-.5)<.01);
 await page.screenshot({path:'/tmp/wheelaa-browser-connected.png'});
 await page.locator('#disconnect').click();await page.waitForSelector('#pairing',{state:'visible'});
 await page.screenshot({path:'/tmp/wheelaa-browser-pairing.png'});
 assert.deepEqual(errors,[]);
 console.log('PASS: real Chromium pairs, displays a fixture frame, sends ordered normalized pointer events, disconnects; no JS errors. Android transport is not part of this browser fixture test.');
}finally{await browser.close();server.close();}
