const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path');
const root=path.resolve(process.argv[2]||'.'),files={};
function add(p){files[p]=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');}
function walk(p){for(const item of fs.readdirSync(path.join(root,p),{withFileTypes:true})){const file=p+'/'+item.name;if(item.isDirectory())walk(file);else if(item.isFile())add(file);}}
for(const p of ['app.js','room-client.js','voice.js','timer-engine.js','styles.css','microphone.html','index.html'])add(p);walk('assets');
fs.writeFileSync(path.join(root,'site-manifest.json'),JSON.stringify({files},null,2)+'\n');