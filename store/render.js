// Renders the Play Store icon (512 x 512) and feature graphic (1024 x 500) into store/.
// Run from the repo root with Playwright installed: node store/render.js .
const {chromium}=require('playwright');const fs=require('fs');
const repo=process.argv[2];
const font='data:font/ttf;base64,'+fs.readFileSync(repo+'/app/src/main/res/font/space_grotesk_bold.ttf').toString('base64');
const glyph=(size)=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="${size}" height="${size}"><g transform="translate(54 54) scale(1.12) translate(-54 -54)">
<path fill="#fff" fill-rule="evenodd" d="M43,40H65A5,5 0,0 1,70 45V63A5,5 0,0 1,65 68H43A5,5 0,0 1,38 63V45A5,5 0,0 1,43 40Z M43,49A3,3 0,1 0,49 49A3,3 0,1 0,43 49Z M41,65L53,53L60,60L64,56L67,59V65Z"/>
<path d="M33,38V33H38M75,38V33H70M33,70V75H38M75,70V75H70" stroke="#fff" stroke-opacity="0.7" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/></g></svg>`;
(async()=>{const b=await chromium.launch();
let p=await b.newPage({viewport:{width:512,height:512}});
await p.setContent(`<body style="margin:0;background:#2E46E6">${glyph(512)}</body>`);
await p.screenshot({path:repo+'/store/icon-512.png'});
p=await b.newPage({viewport:{width:1024,height:500}});
await p.setContent(`<style>@font-face{font-family:SG;src:url(${font})}body{margin:0;width:1024px;height:500px;background:#2E46E6;display:flex;align-items:center;gap:8px;padding-left:90px;box-sizing:border-box;font-family:SG;color:#fff}
h1{font-size:96px;margin:0;letter-spacing:-2px}p{font-family:sans-serif;font-size:34px;margin:8px 0 0;opacity:.85;font-weight:600}</style>
<body>${glyph(300)}<div><h1>Photon</h1><p>Photos small enough<br>for any email.</p></div></body>`);
await p.waitForTimeout(300);
await p.screenshot({path:repo+'/store/feature-graphic.png'});await b.close();})();
