// Sabadaço admin: tema, CSRF do HTMX, toast e player de escuta.
(() => {
    const root = document.documentElement;

    // ----- Tema (o atributo inicial é aplicado no <head> antes de pintar a página) -----
    const currentTheme = () => root.dataset.theme
        || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');

    document.addEventListener('click', (event) => {
        if (!event.target.closest('[data-theme-toggle]')) return;
        const next = currentTheme() === 'dark' ? 'light' : 'dark';
        root.dataset.theme = next;
        try { localStorage.setItem('sabadaco-theme', next); } catch (e) { /* armazenamento indisponível */ }
        document.dispatchEvent(new CustomEvent('themechange', { detail: next }));
    });

    // ----- HTMX: CSRF em toda requisição e toast que some sozinho -----
    document.addEventListener('htmx:configRequest', (event) => {
        const header = document.querySelector('meta[name=_csrf_header]')?.content;
        const token = document.querySelector('meta[name=_csrf]')?.content;
        if (header && token) event.detail.headers[header] = token;
    });
    document.addEventListener('htmx:afterSwap', (event) => {
        if (event.detail.target.id === 'toast') {
            setTimeout(() => { event.detail.target.innerHTML = ''; }, 4500);
        }
    });

    // ----- Link ativo no menu -----
    document.querySelectorAll('.nav-link').forEach((link) => {
        const path = location.pathname;
        const href = link.getAttribute('href');
        if (href === path || (href !== '/admin' && path.startsWith(href))) link.classList.add('active');
    });

    // ----- Ouvir pelo painel -----
    const bar = document.getElementById('listen-bar');
    if (!bar) return;
    const audio = bar.querySelector('audio');
    const title = bar.querySelector('[data-listen-title]');
    const subtitle = bar.querySelector('[data-listen-subtitle]');
    const volume = bar.querySelector('input[type=range]');

    const stop = () => {
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
        bar.classList.remove('open');
    };

    document.addEventListener('click', (event) => {
        const button = event.target.closest('[data-listen]');
        if (!button) return;
        // Novo parâmetro a cada play: evita o navegador reaproveitar um stream antigo
        audio.src = `${button.dataset.listen}?t=${Date.now()}`;
        title.textContent = button.dataset.listenTitle || 'Ouvindo';
        subtitle.textContent = button.dataset.listenGuild || '';
        bar.classList.add('open');
        audio.play().catch(() => bar.classList.add('paused'));
    });

    bar.querySelector('[data-listen-stop]').addEventListener('click', stop);
    volume.addEventListener('input', () => { audio.volume = Number(volume.value); });
    audio.addEventListener('playing', () => bar.classList.remove('paused'));
    audio.addEventListener('waiting', () => bar.classList.add('paused'));
    audio.addEventListener('ended', stop);
})();
