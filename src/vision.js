window.Vision={
  async ocr(source){
    if(!window.Tesseract||!source)return{price:null,text:"OCR недоступен"};
    const w=Math.min(source.width||source.videoWidth||720,720);
    const h=Math.max(1,Math.round((source.height||source.videoHeight||405)*(w/(source.width||source.videoWidth||720))));
    const c=document.createElement("canvas");c.width=w;c.height=h;
    const ctx=c.getContext("2d");ctx.drawImage(source,0,0,w,h);
    try{
      const r=await Tesseract.recognize(c,"eng");
      const text=(r.data&&r.data.text?r.data.text:"").replace(/\s+/g," ").trim();
      const nums=[...text.matchAll(/(?:\d{1,6}[.,]\d{1,8})/g)].map(x=>x[0].replace(",","."));return{price:nums.length?nums[nums.length-1]:null,text:text.slice(0,240)};
    }catch(e){return{price:null,text:"OCR временно недоступен"}}
  },
  extractSeries(source){
    const sw=source.width||source.videoWidth;if(!sw)return null;
    const W=540,H=304,c=document.createElement("canvas");c.width=W;c.height=H;
    const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(source,0,0,W,H);
    let d;try{d=x.getImageData(0,0,W,H).data}catch(e){return null}
    const s=[];
    for(let col=8;col<W-8;col+=6){
      const vals=[];
      for(let row=15;row<H-15;row+=2){
        const i=(row*W+col)*4,r=d[i],g=d[i+1],b=d[i+2],mx=Math.max(r,g,b),mn=Math.min(r,g,b);
        if(mx-mn>45&&mx>90)vals.push(row);
      }
      if(vals.length){vals.sort((a,b)=>a-b);s.push(H-vals[Math.floor(vals.length/2)]);}
    }
    if(s.length<30)return null;
    const mn=Math.min(...s),mx=Math.max(...s);
    return s.map(v=>100+(v-mn)/(mx-mn+1e-6)*100);
  }
};