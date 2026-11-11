// Noodpakket website: entrance, floating phone that tilts with the mouse, a live scan demo in the phone,
// sections that slide in while scrolling, counters, and an expiry timeline that runs out and gets renewed.
(function () {
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const sleep = ms => new Promise(r => setTimeout(r, ms));

  // Year in the footer, header border when scrolled
  document.querySelectorAll('.year').forEach(el => { el.textContent = new Date().getFullYear(); });
  const top = document.getElementById('top');
  const onScroll = () => top && top.classList.toggle('scrolled', window.scrollY > 8);
  window.addEventListener('scroll', onScroll, { passive: true }); onScroll();

  // Slide in while scrolling
  const groups = ['.section-head', '.stat', '.card', '#vragen details', '.platform', '.steps li'];
  const items = [];
  groups.forEach(sel => document.querySelectorAll(sel).forEach((el, i) => {
    el.classList.add('reveal');
    el.style.setProperty('--r', String(i % 6));
  }));
  document.querySelectorAll('.reveal').forEach(el => items.push(el));
  if (reduce || !('IntersectionObserver' in window)) {
    items.forEach(el => el.classList.add('in'));
  } else {
    const io = new IntersectionObserver(entries => entries.forEach(e => {
      if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
    }), { rootMargin: '0px 0px -50px 0px' });
    items.forEach(el => io.observe(el));
  }

  // Counters: count up when the stats come into view
  const counters = document.querySelectorAll('.count');
  if (!reduce && 'IntersectionObserver' in window) {
    const io = new IntersectionObserver(entries => entries.forEach(e => {
      if (!e.isIntersecting) return;
      io.unobserve(e.target);
      const el = e.target, to = +el.dataset.to, start = performance.now();
      el.textContent = '0';
      const step = now => {
        const t = Math.min(1, Math.max(0, (now - start) / 1300));
        el.textContent = String(Math.round(to * (1 - Math.pow(1 - t, 3))));
        if (t < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    }), { threshold: .6 });
    counters.forEach(c => io.observe(c));
  }

  // Hero phone: entrance, then float; tilt with the mouse on desktop
  const stage = document.getElementById('stage');
  const phone = document.getElementById('phone');
  if (stage && phone && !reduce) {
    setTimeout(() => { stage.classList.remove('intro'); stage.classList.add('floating'); }, 2000);
    if (window.matchMedia('(pointer: fine)').matches) {
      stage.addEventListener('mousemove', e => {
        const r = stage.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        phone.style.transform = `perspective(1200px) rotateY(${x * 12}deg) rotateX(${-y * 9}deg)`;
      });
      stage.addEventListener('mouseleave', () => { phone.style.transform = ''; });
    }
  } else if (stage) {
    stage.classList.remove('intro');
  }

  // Live scan demo inside the phone (loops while the hero is visible)
  const $ = id => document.getElementById(id);
  const sheet = $('sheet'), viewfinder = $('viewfinder'), product = $('product'), add = $('add');
  const arc = $('arc'), pct = $('pct'), headline = $('headline'), toast = $('toast');
  const rowWater = $('row-water'), dotWater = $('dot-water'), waterSub = $('water-sub');
  const fields = Array.from(document.querySelectorAll('.field [data-type]'));
  // The big percentage with its bar in the purple header of the app.
  const setRing = p => { arc.style.width = p + '%'; };

  function reset() {
    sheet.classList.remove('open'); viewfinder.classList.remove('done'); product.classList.remove('in');
    toast.classList.remove('in'); rowWater.classList.remove('flash');
    fields.forEach(f => { f.textContent = ''; f.classList.remove('caret'); });
    dotWater.className = 'dot half'; waterSub.textContent = '18 L / 27 L';
    setRing(76); pct.textContent = '76%'; headline.textContent = 'Nog 3 te gaan';
  }

  async function type(el, text) {
    el.classList.add('caret');
    for (let i = 1; i <= text.length; i++) { el.textContent = text.slice(0, i); await sleep(45); }
    el.classList.remove('caret');
  }

  function countRing(from, to, ms) {
    const start = performance.now();
    const step = now => {
      const t = Math.min(1, Math.max(0, (now - start) / ms));
      pct.textContent = Math.round(from + (to - from) * (1 - Math.pow(1 - t, 3))) + '%';
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  let visible = true, running = false;
  async function demo() {
    if (running) return;
    running = true;
    while (visible) {
      reset();
      await sleep(1600);
      sheet.classList.add('open');                      // scanner opens
      await sleep(1900);
      viewfinder.classList.add('done');                 // barcode found
      product.classList.add('in');
      await sleep(500);
      for (const f of fields) { await type(f, f.dataset.type); await sleep(150); }
      await sleep(500);
      add.classList.add('press'); await sleep(160); add.classList.remove('press');
      sheet.classList.remove('open');                   // back to the kit
      await sleep(450);
      rowWater.classList.add('flash'); dotWater.className = 'dot ok'; waterSub.textContent = '27 L / 27 L';
      toast.classList.add('in');
      setRing(91); countRing(76, 91, 1200);
      await sleep(1300);
      toast.classList.remove('in');
      headline.textContent = 'Nog 2 te gaan';
      await sleep(3200);
    }
    running = false;
  }

  if (sheet && !reduce) {
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(entries => {
        visible = entries[0].isIntersecting;
        if (visible) demo();
      }, { threshold: .25 }).observe(stage);
    } else { demo(); }
  } else if (sheet) {
    // Reduced motion: show the end state of the demo
    dotWater.className = 'dot ok'; waterSub.textContent = '27 L / 27 L'; setRing(91); pct.textContent = '91%'; headline.textContent = 'Nog 2 te gaan';
  }

  // Expiry timeline: days count down; batteries expire, a notification appears, then they are renewed
  const timeline = document.getElementById('timeline');
  if (timeline) {
    const rows = Array.from(timeline.querySelectorAll('.t-item')).map(el => ({
      el, start: +el.dataset.days, life: +el.dataset.life, days: +el.dataset.days,
      meter: el.querySelector('.meter i'), label: el.querySelector('.t-days'),
    }));
    const notif = document.getElementById('notif');
    const paint = r => {
      const d = Math.max(0, Math.round(r.days));
      r.meter.style.transform = `scaleX(${Math.max(0.02, Math.min(1, r.days / r.life))})`;
      r.label.textContent = r.days <= 0 ? 'Verlopen' : d + (d === 1 ? ' dag' : ' dagen');
      r.label.className = 't-days' + (r.days <= 7 ? ' bad' : r.days <= 30 ? ' warn' : '');
    };
    rows.forEach(paint);
    if (!reduce) {
      let tlVisible = false, tlRunning = false;
      const batt = rows.find(r => r.el.id === 't-batt');
      async function run() {
        if (tlRunning) return; tlRunning = true;
        while (tlVisible) {
          rows.forEach(r => { r.days = r.start; r.el.classList.remove('expired', 'renewed'); paint(r); });
          notif.classList.remove('in');
          const t0 = performance.now(), RUN = 5200;
          let notified = false;
          await new Promise(done => {
            const step = now => {
              const p = Math.min(1, Math.max(0, (now - t0) / RUN));
              rows.forEach(r => { r.days = r.start - p * 26; paint(r); });
              if (!notified && batt.days <= 7) { notified = true; notif.classList.add('in'); }
              p < 1 ? requestAnimationFrame(step) : done();
            };
            requestAnimationFrame(step);
          });
          batt.el.classList.add('expired');
          await sleep(1800);
          batt.el.classList.add('renewed'); batt.el.classList.remove('expired');
          batt.days = 730; batt.life = 730; paint(batt); batt.label.textContent = 'Vervangen ✓';
          notif.classList.remove('in');
          await sleep(2600);
          batt.life = 365;
        }
        tlRunning = false;
      }
      new IntersectionObserver(entries => { tlVisible = entries[0].isIntersecting; if (tlVisible) run(); }, { threshold: .4 }).observe(timeline);
    }
  }
})();
