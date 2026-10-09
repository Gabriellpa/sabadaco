// Sabadaço admin: aba de métricas em tempo real.
// Toda a aba nasce de GROUPS (uma fonte de verdade): grupos → KPIs + gráficos.
// Regras de visualização seguidas: cores categóricas em ordem fixa (e por entidade, nunca por posição),
// um único eixo Y por gráfico, legenda com o valor atual sempre visível, linhas finas, grade discreta,
// tooltip com crosshair e alternativa em tabela para cada gráfico.
(() => {
    const API = '/admin/api/metrics';
    const POLL_MS = 2000;
    const MAX_AGE_MS = 15 * 60 * 1000;
    const store = (key, value) => { try { localStorage.setItem(key, value); } catch (e) { /* sem armazenamento */ } };
    const load = (key, fallback) => { try { return localStorage.getItem(key) ?? fallback; } catch (e) { return fallback; } };

    // ---------- Formatação ----------
    const nf = (digits = 0) => new Intl.NumberFormat('pt-BR', { maximumFractionDigits: digits, minimumFractionDigits: 0 });
    const fmt = (value, digits = 0) => nf(digits).format(value ?? 0);
    const fmtDuration = (seconds) => {
        const s = Math.round(seconds);
        const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
        return h ? `${h}h ${m}min` : m ? `${m}min ${r}s` : `${r}s`;
    };
    const timeLabel = (t) => new Date(t).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const sum = (list, fn) => list.reduce((acc, item) => acc + (fn(item) || 0), 0);
    const last = (list) => list[list.length - 1];
    const perMinute = (samples, fn) => {
        const recent = samples.slice(-30); // ~1 min
        return recent.length ? sum(recent, fn) * (30 / recent.length) : 0;
    };

    // ---------- Cores (lidas dos tokens do tema atual) ----------
    const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    const slot = (n) => css(`--series-${n}`);
    const alpha = (hex, a) => {
        const v = hex.replace('#', '');
        const [r, g, b] = [0, 2, 4].map((i) => parseInt(v.slice(i, i + 2), 16));
        return `rgba(${r}, ${g}, ${b}, ${a})`;
    };

    // ---------- Definição dos grupos ----------
    // series: { label, slot, value(sample) } para séries no tempo;
    // ranking(ctx) → [{label, value}] para barras horizontais.
    const GROUPS = [
        {
            id: 'overview', label: 'Visão geral',
            kpis: [
                { label: '🎶 Tocando agora', value: (c) => c.last?.playing, unit: 'servidor(es)' },
                { label: '🎧 Ouvintes', value: (c) => c.last?.listeners, unit: 'pessoas' },
                { label: '⌨️ Comandos', value: (c) => perMinute(c.samples, (s) => s.commands), unit: '/min', digits: 1 },
                { label: '⚡ Resposta p95', value: (c) => c.last?.latencyP95Ms, unit: 'ms' },
                { label: '🧠 CPU do bot', value: (c) => c.last?.cpuProcessPct, unit: '%', digits: 1 },
                { label: '💾 Memória heap', value: (c) => c.last?.heapUsedMb, unit: 'MB' },
            ],
            charts: [
                { id: 'ov-sent', title: 'Áudio enviado ao Discord', sub: 'KB/s', type: 'area',
                    series: [{ label: 'Enviado', slot: 1, value: (s) => s.sentKBps }], digits: 1 },
                { id: 'ov-cmds', title: 'Comandos executados', sub: 'por intervalo (agrupado em janelas longas)', type: 'bar',
                    series: [{ label: 'Comandos', slot: 1, value: (s) => s.commands }] },
            ],
        },
        {
            id: 'music', label: 'Música',
            kpis: [
                { label: '🎵 Músicas tocadas', value: (c) => c.summary?.tracksPlayedTotal, unit: 'total' },
                { label: '⚠️ Falhas ao tocar', value: (c) => c.summary?.tracksFailedTotal, unit: 'total' },
                { label: '📡 Enviado ao Discord', value: (c) => c.summary?.sentMbTotal, unit: 'MB', digits: 1 },
                { label: '⬇️ Baixado das fontes', value: (c) => c.summary?.downloadedMbTotal, unit: 'MB', digits: 1 },
            ],
            charts: [
                { id: 'mu-traffic', title: 'Tráfego de áudio', sub: 'KB/s', type: 'line', digits: 1,
                    series: [
                        { label: 'Enviado ao Discord', slot: 1, value: (s) => s.sentKBps },
                        { label: 'Baixado das fontes', slot: 2, value: (s) => s.downloadedKBps },
                    ] },
                { id: 'mu-started', title: 'Músicas iniciadas', sub: 'por intervalo', type: 'bar',
                    series: [{ label: 'Iniciadas', slot: 1, value: (s) => s.tracksStarted }] },
                { id: 'mu-queue', title: 'Músicas na fila', sub: 'todos os servidores', type: 'area',
                    series: [{ label: 'Na fila', slot: 3, value: (s) => s.queued }] },
                { id: 'mu-top', title: 'Mais tocadas', sub: 'desde que o bot subiu', type: 'hbar', slot: 1,
                    ranking: (c) => c.summary?.topTracks ?? [] },
            ],
        },
        {
            id: 'commands', label: 'Comandos',
            kpis: [
                { label: '⌨️ Comandos', value: (c) => perMinute(c.samples, (s) => s.commands), unit: '/min', digits: 1 },
                { label: '❌ Erros na janela', value: (c) => sum(c.samples, (s) => s.commandErrors), unit: '' },
                { label: '⚡ p95', value: (c) => c.last?.latencyP95Ms, unit: 'ms' },
                { label: '🐢 p99', value: (c) => c.last?.latencyP99Ms, unit: 'ms' },
            ],
            charts: [
                { id: 'cm-latency', title: 'Tempo de resposta', sub: 'ms · média do intervalo e percentis (janela móvel de 2 min)', type: 'line',
                    series: [
                        { label: 'Média', slot: 1, value: (s) => (s.commands ? s.latencyMeanMs : null) },
                        { label: 'p95', slot: 2, value: (s) => s.latencyP95Ms },
                        { label: 'p99', slot: 3, value: (s) => s.latencyP99Ms },
                    ] },
                { id: 'cm-by-name', title: 'Comandos por tipo', sub: 'por intervalo · top 5 + outros', type: 'stacked', byName: true },
                { id: 'cm-errors', title: 'Erros', sub: 'por intervalo (regra de negócio + falhas)', type: 'bar', status: 'critical',
                    series: [{ label: 'Erros', value: (s) => s.commandErrors }] },
                { id: 'cm-top', title: 'Mais usados', sub: 'desde que o bot subiu', type: 'hbar', slot: 1,
                    ranking: (c) => c.summary?.topCommands ?? [] },
            ],
        },
        {
            id: 'users', label: 'Usuários',
            kpis: [
                { label: '🟢 Ativos (5 min)', value: (c) => c.last?.activeUsers, unit: '' },
                { label: '🕐 Ativos (1 h)', value: (c) => c.summary?.activeUsersHour, unit: '' },
                { label: '👥 Já usaram o bot', value: (c) => c.summary?.knownUsers, unit: '' },
                { label: '🎧 Ouvintes agora', value: (c) => c.last?.listeners, unit: '' },
            ],
            charts: [
                { id: 'us-active', title: 'Usuários ativos', sub: 'interagiram nos últimos 5 min', type: 'area',
                    series: [{ label: 'Ativos', slot: 1, value: (s) => s.activeUsers }] },
                { id: 'us-listeners', title: 'Ouvintes em canais de voz', sub: 'pessoas no canal com o bot', type: 'area',
                    series: [{ label: 'Ouvintes', slot: 3, value: (s) => s.listeners }] },
                { id: 'us-top', title: 'Quem mais usa', sub: 'comandos e botões', type: 'hbar', slot: 1,
                    ranking: (c) => c.summary?.topUsers ?? [] },
            ],
        },
        {
            id: 'playlists', label: 'Playlists',
            kpis: [
                { label: '📚 Playlists', value: (c) => c.summary?.playlists, unit: '' },
                { label: '🎼 Músicas salvas', value: (c) => c.summary?.playlistTracks, unit: '' },
                { label: '🏷️ Com apelido', value: (c) => c.summary?.aliasedTracks, unit: '' },
                { label: '▶️ Tocadas de playlist', value: (c) => c.summary?.playlistPlays, unit: '' },
            ],
            charts: [
                { id: 'pl-scope', title: 'Playlists por escopo', sub: 'quantidade', type: 'hbar', perBar: true,
                    ranking: (c) => [{ label: 'Do servidor', value: c.summary?.guildPlaylists ?? 0 }, { label: 'Globais', value: c.summary?.globalPlaylists ?? 0 }] },
                { id: 'pl-origin', title: 'Músicas tocadas por origem', sub: 'avulsas têm prioridade sobre playlist', type: 'hbar', perBar: true,
                    ranking: (c) => [{ label: 'Avulsas', value: c.summary?.singlePlays ?? 0 }, { label: 'De playlist', value: c.summary?.playlistPlays ?? 0 }] },
                { id: 'pl-biggest', title: 'Maiores playlists', sub: 'músicas', type: 'hbar', slot: 1,
                    ranking: (c) => c.summary?.biggestPlaylists ?? [] },
            ],
        },
        {
            id: 'system', label: 'Sistema',
            kpis: [
                { label: '🧠 CPU do bot', value: (c) => c.last?.cpuProcessPct, unit: '%', digits: 1 },
                { label: '⏱️ CPU total consumida', value: (c) => c.summary?.cpuTimeSeconds, format: fmtDuration,
                    hint: (c) => c.summary ? `em ${c.summary.cpus} núcleo(s)` : '' },
                { label: '💾 Heap', value: (c) => c.last?.heapUsedMb, unit: 'MB',
                    hint: (c) => c.summary ? `de ${fmt(c.summary.heapMaxMb)} MB máx.` : '' },
                { label: '🧵 Threads', value: (c) => c.last?.threads, unit: '' },
                { label: '🚀 No ar há', value: (c) => c.summary?.uptimeSeconds, format: fmtDuration },
            ],
            charts: [
                { id: 'sy-cpu', title: 'CPU', sub: '% · processo do bot × máquina inteira', type: 'line', max: 100, digits: 1,
                    series: [
                        { label: 'Bot', slot: 1, value: (s) => s.cpuProcessPct },
                        { label: 'Máquina', slot: 2, value: (s) => s.cpuSystemPct },
                    ] },
                { id: 'sy-memory', title: 'Memória', sub: 'MB', type: 'line', digits: 1,
                    series: [
                        { label: 'Heap usado', slot: 1, value: (s) => s.heapUsedMb },
                        { label: 'Heap reservado', slot: 2, value: (s) => s.heapCommittedMb },
                        { label: 'Fora do heap', slot: 3, value: (s) => s.nonHeapMb },
                    ] },
                { id: 'sy-gc', title: 'Pausas de GC', sub: 'ms por intervalo', type: 'bar',
                    series: [{ label: 'GC', slot: 4, value: (s) => s.gcPauseMs }] },
                { id: 'sy-threads', title: 'Threads de plataforma', sub: 'virtual threads não entram na conta', type: 'line',
                    series: [{ label: 'Threads', slot: 6, value: (s) => s.threads }] },
            ],
        },
        {
            id: 'discord', label: 'Discord',
            kpis: [
                { label: '🏠 Servidores', value: (c) => c.summary?.guilds, unit: '' },
                { label: '🔊 Conexões de voz', value: (c) => c.last?.voiceConnections, unit: '' },
                { label: '📶 Ping do gateway', value: (c) => c.last?.gatewayPingMs, unit: 'ms' },
                { label: '🔎 Buscas na janela', value: (c) => sum(c.samples, (s) => s.searches), unit: '' },
            ],
            charts: [
                { id: 'dc-ping', title: 'Ping do gateway', sub: 'ms', type: 'area',
                    series: [{ label: 'Ping', slot: 1, value: (s) => s.gatewayPingMs }] },
                { id: 'dc-search', title: 'Buscas no YouTube', sub: 'por intervalo · do YouTube × do cache', type: 'stacked',
                    series: [
                        { label: 'Do YouTube', slot: 1, value: (s) => s.searches - s.searchesCached },
                        { label: 'Do cache', slot: 3, value: (s) => s.searchesCached },
                    ] },
            ],
        },
    ];

    // ---------- Estado ----------
    const state = {
        samples: [],
        summary: null,
        windowMin: Number(load('sabadaco-metrics-window', '5')),
        active: load('sabadaco-metrics-tab', 'overview'),
        paused: false,
        charts: new Map(),
        kpiValues: new Map(),
        nameSlots: new Map(), // cor por comando, fixada na ordem em que aparece
    };
    if (!GROUPS.some((g) => g.id === state.active)) state.active = 'overview';

    const windowed = () => {
        const since = Date.now() - state.windowMin * 60 * 1000;
        return state.samples.filter((s) => s.t >= since);
    };
    const context = () => {
        const samples = windowed();
        return { samples, last: last(samples) ?? last(state.samples), summary: state.summary };
    };

    // ---------- DOM ----------
    const tabs = document.getElementById('metric-tabs');
    const indicator = tabs.querySelector('.tab-indicator');
    const panels = document.getElementById('metric-panels');

    GROUPS.forEach((group) => {
        const tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'tab';
        tab.role = 'tab';
        tab.dataset.group = group.id;
        tab.textContent = group.label;
        tabs.appendChild(tab);

        const panel = document.createElement('section');
        panel.className = 'panel';
        panel.dataset.group = group.id;
        panel.innerHTML = `
            <div class="kpis">${group.kpis.map((kpi, i) => `
                <div class="card kpi hoverable" data-kpi="${group.id}-${i}">
                    <div class="label">${kpi.label}</div>
                    <div class="value"><span data-value>—</span><span class="unit">${kpi.unit ?? ''}</span></div>
                    <div class="hint" data-hint></div>
                </div>`).join('')}
            </div>
            <div class="charts">${group.charts.map((chart) => `
                <article class="card chart-card" data-chart="${chart.id}">
                    <div class="card-head">
                        <div><h2>${chart.title}</h2><div class="chart-sub">${chart.sub ?? ''}</div></div>
                        <button type="button" class="ghost chart-toggle" data-table-toggle>Tabela</button>
                    </div>
                    <div class="chart-box"><canvas aria-label="${chart.title}" role="img"></canvas></div>
                    <div class="chart-table table-wrap"></div>
                    <div class="legend"></div>
                </article>`).join('')}
            </div>`;
        panels.appendChild(panel);
    });

    const moveIndicator = () => {
        const tab = tabs.querySelector(`.tab[data-group="${state.active}"]`);
        if (!tab) return;
        indicator.style.width = `${tab.offsetWidth}px`;
        indicator.style.transform = `translateX(${tab.offsetLeft - 4}px)`;
        tab.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    };

    const activate = (groupId) => {
        state.active = groupId;
        store('sabadaco-metrics-tab', groupId);
        tabs.querySelectorAll('.tab').forEach((t) => {
            const on = t.dataset.group === groupId;
            t.classList.toggle('active', on);
            t.setAttribute('aria-selected', on);
        });
        panels.querySelectorAll('.panel').forEach((p) => p.classList.toggle('active', p.dataset.group === groupId));
        moveIndicator();
        render(true);
    };

    tabs.addEventListener('click', (event) => {
        const tab = event.target.closest('.tab');
        if (tab) activate(tab.dataset.group);
    });

    const windowButtons = document.querySelectorAll('#metric-window button');
    const markWindow = () => windowButtons.forEach((b) => b.classList.toggle('active', Number(b.dataset.window) === state.windowMin));
    windowButtons.forEach((button) => button.addEventListener('click', () => {
        state.windowMin = Number(button.dataset.window);
        store('sabadaco-metrics-window', String(state.windowMin));
        markWindow();
        render(false);
    }));
    markWindow();

    const pauseButton = document.getElementById('metric-pause');
    const liveStatus = document.getElementById('live-status');
    pauseButton.addEventListener('click', () => {
        state.paused = !state.paused;
        pauseButton.textContent = state.paused ? '▶ Retomar' : '⏸ Pausar';
        liveStatus.textContent = state.paused ? 'pausado' : 'ao vivo';
        liveStatus.style.opacity = state.paused ? 0.5 : 1;
    });

    panels.addEventListener('click', (event) => {
        const toggle = event.target.closest('[data-table-toggle]');
        if (!toggle) return;
        const card = toggle.closest('.chart-card');
        card.classList.toggle('show-table');
        toggle.textContent = card.classList.contains('show-table') ? 'Gráfico' : 'Tabela';
        renderTable(card.dataset.chart);
    });

    // ---------- KPIs (contagem suave; sem "bounce", que a cada 2s parecia piscar) ----------
    const easeOutCubic = (x) => 1 - Math.pow(1 - x, 3);
    const animateNumber = (el, from, to, format) => {
        const start = performance.now();
        const duration = 450;
        const step = (now) => {
            const p = Math.min(1, (now - start) / duration);
            el.textContent = format(from + (to - from) * easeOutCubic(p));
            if (p < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    };

    const renderKpis = (group, ctx) => {
        group.kpis.forEach((kpi, i) => {
            const key = `${group.id}-${i}`;
            const card = panels.querySelector(`[data-kpi="${key}"]`);
            const raw = kpi.value(ctx);
            const value = Number.isFinite(raw) ? raw : 0;
            const format = kpi.format ?? ((v) => fmt(v, kpi.digits ?? 0));
            const previous = state.kpiValues.get(key);
            const el = card.querySelector('[data-value]');
            if (previous === undefined) animateNumber(el, 0, value, format);
            else if (previous !== value) animateNumber(el, previous, value, format);
            state.kpiValues.set(key, value);
            card.querySelector('[data-hint]').textContent = kpi.hint ? kpi.hint(ctx) : '';
        });
    };

    // ---------- Gráficos ----------
    const baseOptions = (spec) => ({
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 650, easing: 'easeOutQuart' },
        animations: spec.type === 'hbar' ? {} : { x: { duration: 0 } }, // sem "deslizar" na horizontal ao chegar ponto novo
        interaction: { mode: spec.type === 'hbar' ? 'nearest' : 'index', intersect: false },
        plugins: {
            legend: { display: false },
            tooltip: {
                backgroundColor: css('--surface'), titleColor: css('--text'), bodyColor: css('--text-2'),
                borderColor: css('--border-strong'), borderWidth: 1, padding: 10, cornerRadius: 10,
                boxPadding: 4, usePointStyle: true,
                callbacks: { label: (item) => ` ${item.dataset.label}: ${fmt(item.parsed[spec.type === 'hbar' ? 'x' : 'y'], spec.digits ?? 1)}` },
            },
        },
        scales: {
            x: {
                stacked: spec.type === 'stacked',
                grid: { display: spec.type === 'hbar', color: css('--grid') },
                border: { color: css('--grid') },
                ticks: { color: css('--muted'), maxTicksLimit: 6, maxRotation: 0, font: { size: 11 }, precision: spec.type === 'hbar' ? 0 : undefined },
                beginAtZero: spec.type === 'hbar',
            },
            y: {
                stacked: spec.type === 'stacked',
                beginAtZero: true,
                max: spec.max,
                grid: { display: spec.type !== 'hbar', color: css('--grid') },
                border: { display: false },
                ticks: { color: spec.type === 'hbar' ? css('--text-2') : css('--muted'), maxTicksLimit: 5, font: { size: 11 }, precision: spec.digits ? undefined : 0,
                    callback: spec.type === 'hbar' ? function (v) { const l = this.getLabelForValue(v); return l.length > 28 ? `${l.slice(0, 27)}…` : l; } : undefined },
            },
        },
    });

    const seriesDataset = (spec, series, values) => {
        const color = spec.status ? css(`--${spec.status}`) : slot(series.slot);
        const line = spec.type === 'line' || spec.type === 'area';
        return {
            label: series.label,
            data: values,
            borderColor: color,
            backgroundColor: line ? alpha(color, spec.type === 'area' ? 0.16 : 0) : color,
            fill: spec.type === 'area' ? 'origin' : false,
            borderWidth: line ? 2 : (spec.type === 'stacked' ? { top: 2 } : 0),
            borderSkipped: spec.type === 'stacked' ? 'bottom' : 'start',
            ...(spec.type === 'stacked' ? { borderColor: css('--surface') } : {}),
            tension: 0.35,
            spanGaps: true,
            pointRadius: 0,
            pointHoverRadius: 4,
            pointHitRadius: 14,
            borderRadius: line ? 0 : 4,
            maxBarThickness: 26,
        };
    };

    const byNameDatasets = (spec, samples) => {
        // Top 5 comandos da janela mantêm cor própria (fixada por nome); o resto vira "Outros"
        const totals = new Map();
        samples.forEach((s) => Object.entries(s.commandsByName ?? {}).forEach(([name, n]) => totals.set(name, (totals.get(name) ?? 0) + n)));
        const top = [...totals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([name]) => name);
        top.forEach((name) => {
            if (!state.nameSlots.has(name) && state.nameSlots.size < 5) state.nameSlots.set(name, state.nameSlots.size + 1);
        });
        const named = top.filter((name) => state.nameSlots.has(name));
        const datasets = named.map((name) => ({
            ...seriesDataset(spec, { label: name.includes(':') ? `🔘 ${name}` : `/${name}`, slot: state.nameSlots.get(name) }, samples.map((s) => s.commandsByName?.[name] ?? 0)),
        }));
        const others = samples.map((s) => Object.entries(s.commandsByName ?? {}).filter(([n]) => !named.includes(n)).reduce((a, [, v]) => a + v, 0));
        if (others.some((v) => v > 0)) {
            datasets.push({ ...seriesDataset(spec, { label: 'Outros', slot: 1 }, others), backgroundColor: css('--series-other') });
        }
        return datasets;
    };

    // Barras de contagem: agrupa intervalos para não virar "fios" em janelas longas (no máximo ~30 barras)
    const MAX_BARS = 30;
    const bucketize = (samples) => {
        const size = Math.max(1, Math.ceil(samples.length / MAX_BARS));
        if (size === 1) return samples;
        const merged = [];
        for (let i = 0; i < samples.length; i += size) {
            const group = samples.slice(i, i + size);
            const point = { ...last(group), commandsByName: {} };
            Object.keys(point).forEach((key) => {
                if (typeof point[key] === 'number' && key !== 't') point[key] = sum(group, (s) => s[key]);
            });
            group.forEach((s) => Object.entries(s.commandsByName ?? {}).forEach(([name, n]) => {
                point.commandsByName[name] = (point.commandsByName[name] ?? 0) + n;
            }));
            merged.push(point);
        }
        return merged;
    };

    const chartData = (spec, ctx) => {
        if (spec.ranking) {
            const rows = spec.ranking(ctx);
            const colors = spec.perBar ? rows.map((_, i) => slot(i + 1)) : rows.map(() => slot(spec.slot ?? 1));
            return {
                labels: rows.map((r) => r.label),
                datasets: [{ label: spec.title, data: rows.map((r) => r.value), backgroundColor: colors, borderRadius: 4, borderSkipped: 'start', maxBarThickness: 22 }],
            };
        }
        const points = spec.type === 'bar' || spec.type === 'stacked' ? bucketize(ctx.samples) : ctx.samples;
        const labels = points.map((s) => timeLabel(s.t));
        const datasets = spec.byName ? byNameDatasets(spec, points)
            : spec.series.map((series) => seriesDataset(spec, series, points.map((s) => { const v = series.value(s); return v === null ? null : Math.max(0, v ?? 0); })));
        return { labels, datasets };
    };

    const renderLegend = (spec, card, data) => {
        const legend = card.querySelector('.legend');
        if (spec.ranking) {
            legend.innerHTML = data.labels.length ? '' : '<span class="muted">Sem dados ainda.</span>';
            return;
        }
        // Legenda sempre visível com o valor atual de cada série (não depende de hover nem só da cor)
        legend.innerHTML = data.datasets.map((ds) => {
            const color = typeof ds.backgroundColor === 'string' && !ds.backgroundColor.startsWith('rgba') ? ds.backgroundColor : ds.borderColor;
            const counts = spec.type === 'bar' || spec.type === 'stacked';
            const value = counts ? ds.data.reduce((a, v) => a + (v ?? 0), 0) : [...ds.data].reverse().find((v) => v !== null) ?? 0;
            return `<span><i style="background:${spec.type === 'stacked' ? ds.backgroundColor : color}"></i>${ds.label} <b>${fmt(value, spec.digits ?? 0)}</b></span>`;
        }).join('') + ((spec.type === 'bar' || spec.type === 'stacked') ? '<span class="muted">total na janela</span>' : '<span class="muted">valor atual</span>');
    };

    const renderTable = (chartId) => {
        const entry = state.charts.get(chartId);
        const card = panels.querySelector(`[data-chart="${chartId}"]`);
        if (!entry || !card.classList.contains('show-table')) return;
        const { chart, spec } = entry;
        const labels = chart.data.labels;
        const rows = spec.ranking ? labels.map((_, i) => i) : labels.map((_, i) => i).slice(-12).reverse();
        const header = spec.ranking ? '<th>Item</th><th class="num">Valor</th>'
            : `<th>Hora</th>${chart.data.datasets.map((d) => `<th class="num">${d.label}</th>`).join('')}`;
        const body = rows.map((i) => spec.ranking
            ? `<tr><td>${labels[i]}</td><td class="num">${fmt(chart.data.datasets[0].data[i])}</td></tr>`
            : `<tr><td>${labels[i]}</td>${chart.data.datasets.map((d) => `<td class="num">${fmt(d.data[i], spec.digits ?? 0)}</td>`).join('')}</tr>`).join('');
        card.querySelector('.chart-table').innerHTML = `<table><thead><tr>${header}</tr></thead><tbody>${body || '<tr><td class="muted">Sem dados ainda.</td></tr>'}</tbody></table>`;
    };

    const renderChart = (spec, ctx, firstShow) => {
        const card = panels.querySelector(`[data-chart="${spec.id}"]`);
        const data = chartData(spec, ctx);
        let entry = state.charts.get(spec.id);
        if (!entry) {
            const type = spec.type === 'hbar' || spec.type === 'stacked' ? 'bar' : spec.type === 'area' ? 'line' : spec.type;
            const options = baseOptions(spec);
            if (spec.type === 'hbar') options.indexAxis = 'y';
            // Barras "nascem" com um leve bounce
            if (type === 'bar') options.animation = { duration: 900, easing: 'easeOutBack' };
            entry = { spec, chart: new Chart(card.querySelector('canvas'), { type, data, options }) };
            state.charts.set(spec.id, entry);
            if (type === 'bar') setTimeout(() => { entry.chart.options.animation = { duration: 450, easing: 'easeOutQuart' }; }, 1000);
        } else {
            entry.chart.data.labels = data.labels;
            // Reaproveita os datasets existentes (mesma ordem/rótulo) para a transição ficar suave
            const sameShape = entry.chart.data.datasets.length === data.datasets.length
                && entry.chart.data.datasets.every((d, i) => d.label === data.datasets[i].label);
            if (sameShape) {
                entry.chart.data.datasets.forEach((d, i) => Object.assign(d, data.datasets[i]));
            } else {
                entry.chart.data.datasets = data.datasets;
            }
            entry.chart.update();
        }
        renderLegend(spec, card, data);
        renderTable(spec.id);
    };

    function render(firstShow) {
        const group = GROUPS.find((g) => g.id === state.active);
        if (!group || typeof Chart === 'undefined') return;
        const ctx = context();
        renderKpis(group, ctx);
        group.charts.forEach((spec) => renderChart(spec, ctx, firstShow));
    }

    // Tema mudou: recria os gráficos com as cores do novo tema
    document.addEventListener('themechange', () => {
        state.charts.forEach(({ chart }) => chart.destroy());
        state.charts.clear();
        render(true);
    });
    window.addEventListener('resize', moveIndicator);

    // ---------- Dados ----------
    const poll = async () => {
        if (!state.paused) {
            try {
                const since = last(state.samples)?.t ?? 0;
                const response = await fetch(`${API}?since=${since}`, { headers: { Accept: 'application/json' } });
                if (response.ok) {
                    const body = await response.json();
                    state.samples.push(...body.samples);
                    const cutoff = Date.now() - MAX_AGE_MS;
                    state.samples = state.samples.filter((s) => s.t >= cutoff);
                    state.summary = body.summary;
                    liveStatus.textContent = 'ao vivo';
                    render(false);
                } else if (response.status === 401 || response.redirected) {
                    location.reload();
                }
            } catch (e) {
                liveStatus.textContent = 'reconectando…';
            }
        }
        setTimeout(poll, POLL_MS);
    };

    const start = () => {
        if (typeof Chart === 'undefined') { setTimeout(start, 50); return; }
        Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
        activate(state.active);
        poll();
    };
    start();
})();
