const screen=document.getElementById("screen");
let stream=null,busy=false,ocrBusy=false,lastOcrAt=0,lastFrameAt=0;
const $=id=>document.getElementById(id);
function setText(id,value){const e=$(id);if(e)e.textContent=value;}
function eventLog(s){const box=$("events");if(!box)return;const e=document.createElement("div");e.className="event";e.textContent=s;box.prepend(e);while(box.children.length>12)box.lastChild.remove();}
function sync(id,value){setText(id,value);}
function syncAnalysis(data){
  const map={
    decision:data.decision,direction:data.direction,confidence:data.confidence+"%",
    risk:data.risk+"%",price:data.price,trend:data.trend,rsi:data.rsi,macd:data.macd,
    macdSignal:data.macdSignal,hist:data.hist,support:data.support,resistance:data.resistance,
    strength:data.strength+"%",candles:String(data.candles),ocrPrice:data.ocrPrice||"—"
  };
  Object.keys(map).forEach(k=>sync(k,map[k]));
  sync("decisionHome",data.decision);sync("directionHome",data.direction);
  sync("confidenceHome",data.confidence+"%");sync("riskHome",data.risk+"%");
  sync("priceHome",data.price);sync("trendHome",data.trend);sync("rsiHome",data.rsi);
  sync("macdHome",data.macd);sync("supportHome",data.support);sync("resistanceHome",data.resistance);
  sync("why",data.why);sync("whyHome",data.why);
  const bar=$("confidenceBar");if(bar)bar.style.width=Math.max(0,Math.min(100,data.confidence))+"%";
}
window.nativeScreenFrame=data=>{
  const now=Date.now(); if(now-lastFrameAt<220)return; lastFrameAt=now;
  const img=new Image();
  img.onload=()=>{
    try{
      if(!screen)return;
      const maxW=720;
      const ratio=Math.min(1,maxW/img.naturalWidth);
      screen.width=Math.max(1,Math.round(img.naturalWidth*ratio));
      screen.height=Math.max(1,Math.round(img.naturalHeight*ratio));
      const ctx=screen.getContext("2d",{alpha:false});
      ctx.drawImage(img,0,0,screen.width,screen.height);
      setText("status","● LIVE");setText("homeLive","LIVE");
    }catch(e){eventLog("Ошибка кадра: "+(e&&e.message?e.message:"неизвестная ошибка"))}
  };
  img.onerror=()=>eventLog("Не удалось обработать кадр экрана");
  img.src=data;
};
window.showView=v=>{
  document.querySelectorAll(".view").forEach(x=>x.classList.toggle("active",x.dataset.view===v));
  document.querySelectorAll(".nav").forEach(x=>x.classList.toggle("active",x.dataset.nav===v));
};
window.nativeCaptureStarted=()=>{setText("status","● LIVE");setText("homeLive","LIVE");};
window.nativeCaptureStopped=()=>{setText("status","● READY");setText("homeLive","READY");};
window.nativeCaptureDenied=()=>{setText("status","● READY");setText("homeLive","READY");eventLog("Захват экрана отменён");};

async function analyse(){
  if(busy||!screen||!screen.width)return;
  busy=true;setText("status","● ANALYZING");
  try{
    const a=Vision.extractSeries(screen);
    if(!a||a.length<30){
      setText("decision","НЕТ ДАННЫХ");setText("decisionSide","НЕТ ДАННЫХ");setText("decisionHome","НЕТ ДАННЫХ");
      setText("why","График не распознан. Откройте график крупнее и оставьте его на экране.");
      setText("whyHome","График пока не распознан. Откройте график крупнее и оставьте его на экране.");
      return;
    }
    const r=Indicators.rsi(a),m=Indicators.macd(a),lv=Indicators.levels(a);
    const rv=r.filter(x=>Number.isFinite(x)).slice(-1)[0];
    const mv=m.macd.filter(x=>Number.isFinite(x)).slice(-1)[0];
    const sv=m.signal.filter(x=>Number.isFinite(x)).slice(-1)[0];
    const hv=m.hist.filter(x=>Number.isFinite(x)).slice(-1)[0];
    const trend=a[a.length-1]-a[Math.max(0,a.length-20)];
    if(!Number.isFinite(rv)||!Number.isFinite(mv)||!Number.isFinite(sv))return;
    const dir=trend>2&&mv>sv?"up":trend<-2&&mv<sv?"down":"flat";
    const confidence=Math.max(1,Math.min(96,Math.round(55+Math.abs(trend)*1.4+(dir!=="flat"?12:0))));
    const risk=Math.max(8,Math.min(92,Math.round(100-confidence+((rv>70||rv<30)?12:0))));
    const go=dir!=="flat"&&confidence>=65&&risk<48;
    const decision=go?(dir==="up"?"ВВЕРХ":"ВНИЗ"):"ЖДАТЬ";
    const direction=dir==="up"?"↑ Положительная динамика":dir==="down"?"↓ Отрицательная динамика":"→ Боковое движение";
    const why=go?"Есть подтверждение по тренду и MACD.":"Недостаточно подтверждений или риск слишком высок.";
    const data={
      decision,direction,confidence,risk,price:a[a.length-1].toFixed(2),
      trend:trend>=0?"Восходящий":"Нисходящий",rsi:rv.toFixed(1),macd:mv.toFixed(3),
      macdSignal:sv.toFixed(3),hist:Number.isFinite(hv)?hv.toFixed(3):"0.000",
      support:Number(lv.support).toFixed(2),resistance:Number(lv.resistance).toFixed(2),
      strength:Math.min(99,Math.round(50+Math.abs(trend)*3)),candles:a.length,why,ocrPrice:"—"
    };
    if($("ocr")&&$("ocr").value==="on"&&!ocrBusy&&Date.now()-lastOcrAt>15000){
      ocrBusy=true;lastOcrAt=Date.now();
      try{const o=await Vision.ocr(screen);data.ocrPrice=o.price||"—";setText("ocrText",o.text||"—");}
      catch(e){setText("ocrText","OCR временно недоступен");}
      finally{ocrBusy=false;}
    }
    syncAnalysis(data);
    eventLog(decision+" · "+($("horizon")?$("horizon").value:"5")+"м · confidence "+confidence+"% · risk "+risk+"% · RSI "+rv.toFixed(1));
  }catch(e){
    eventLog("Ошибка анализа: "+(e&&e.message?e.message:"неизвестная ошибка"));
    setText("whyHome","Анализ временно перезапущен. Захват экрана продолжает работать.");
  }finally{busy=false;setText("status","● LIVE");}
}
function startCapture(){
  if(window.AndroidCapture&&AndroidCapture.startScreenCapture)AndroidCapture.startScreenCapture();
  else eventLog("Нативный захват доступен только в Android-приложении");
}
function stopCapture(){
  try{if(window.AndroidCapture&&AndroidCapture.stopScreenCapture)AndroidCapture.stopScreenCapture();}catch(e){}
  if(stream){stream.getTracks().forEach(t=>t.stop());stream=null;}
  setText("status","● READY");setText("homeLive","READY");
}
const start=$("start");if(start)start.onclick=startCapture;
const stop=$("stop");if(stop)stop.onclick=stopCapture;
const analyseBtn=$("analyse");if(analyseBtn)analyseBtn.onclick=analyse;
setInterval(()=>{if(screen&&screen.width)analyse();},4000);
(function(){
  function show(v){window.showView(v);}
  document.querySelectorAll(".nav").forEach(n=>n.onclick=()=>show(n.dataset.nav));
  document.querySelectorAll("[data-go]").forEach(n=>n.onclick=()=>show(n.dataset.go));
  const settings=$("settingsBtn");if(settings)settings.onclick=()=>show("profile");
  const hz=$("horizon");if(hz)hz.onchange=e=>{const x=$("horizonLabel");if(x)x.textContent=e.target.value+" минут";};
})();