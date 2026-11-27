// Новогодний снегопад на заднем плане (лёгкий canvas, не мешает кликам)
(function () {
  if (document.getElementById('twoblocksSnowCanvas')) return;
  const canvas = document.createElement('canvas');
  canvas.id = 'twoblocksSnowCanvas';
  canvas.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:0;';
  document.body.prepend(canvas);

  const ctx = canvas.getContext('2d');
  let w = 0, h = 0;
  function resize() {
    w = canvas.width = window.innerWidth;
    h = canvas.height = window.innerHeight;
  }
  resize();
  window.addEventListener('resize', resize, { passive: true });

  const count = Math.min(90, Math.max(40, Math.floor(window.innerWidth / 18)));
  const flakes = Array.from({ length: count }, () => ({
    x: Math.random() * w,
    y: Math.random() * h,
    r: 1.2 + Math.random() * 2.8,
    vy: 0.45 + Math.random() * 1.15,
    vx: (Math.random() - 0.5) * 0.4,
    phase: Math.random() * Math.PI * 2,
    alpha: 0.35 + Math.random() * 0.55
  }));

  function tick() {
    ctx.clearRect(0, 0, w, h);
    for (const f of flakes) {
      f.phase += 0.018;
      f.x += f.vx + Math.sin(f.phase) * 0.35;
      f.y += f.vy;

      if (f.y > h + 6) {
        f.y = -6;
        f.x = Math.random() * w;
      }
      if (f.x > w + 6) f.x = -6;
      if (f.x < -6) f.x = w + 6;

      ctx.beginPath();
      ctx.arc(f.x, f.y, f.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(235, 246, 255, ${f.alpha})`;
      ctx.fill();
    }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
})();
