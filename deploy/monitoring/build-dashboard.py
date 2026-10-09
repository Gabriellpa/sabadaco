#!/usr/bin/env python3
"""Gera sabadaco-dashboard.json (dashboard do Grafana) a partir da lista de painéis abaixo.

    python deploy/monitoring/build-dashboard.py

Edite este script, não o JSON. As métricas vêm de /actuator/prometheus (porta 8081), coletadas pelo PodMonitor.
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parent / "sabadaco-dashboard.json"
SEL = 'namespace="$namespace"'
POD = 'namespace="$namespace", pod=~"sabadaco-bot.*"'

panels = []
y = 0


def row(title):
    global y
    panels.append({"type": "row", "title": title, "collapsed": False,
                   "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}})
    y += 1


def stat(title, expr, x, w=4, unit="none", decimals=0):
    panels.append({
        "type": "stat", "title": title,
        "gridPos": {"h": 4, "w": w, "x": x, "y": y},
        "datasource": {"type": "prometheus", "uid": "${datasource}"},
        "targets": [{"expr": expr, "refId": "A", "instant": True}],
        "fieldConfig": {"defaults": {"unit": unit, "decimals": decimals,
                                     "color": {"mode": "fixed", "fixedColor": "#e07a2e"}}, "overrides": []},
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "area",
                    "textMode": "value", "justifyMode": "center"},
    })


def series(title, targets, x, w=12, h=8, unit="none", stack=False):
    panels.append({
        "type": "timeseries", "title": title,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "datasource": {"type": "prometheus", "uid": "${datasource}"},
        "targets": [{"expr": expr, "legendFormat": legend, "refId": chr(65 + i)}
                    for i, (expr, legend) in enumerate(targets)],
        "fieldConfig": {"defaults": {"unit": unit, "custom": {
            "lineWidth": 2, "fillOpacity": 12, "showPoints": "never", "gradientMode": "opacity",
            "stacking": {"mode": "normal" if stack else "none"}}}, "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom", "calcs": ["lastNotNull", "max"]},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
    })


def advance(h):
    global y
    y += h


row("Agora")
stat("Tocando agora", f"sum(sabadaco_players_active{{{SEL}}})", 0)
stat("Ouvintes", f"sum(sabadaco_discord_listeners{{{SEL}}})", 4)
stat("Usuários ativos (5 min)", f"sum(sabadaco_users_active{{{SEL}}})", 8)
stat("Servidores", f"max(sabadaco_discord_guilds{{{SEL}}})", 12)
stat("Ping do gateway", f"max(sabadaco_discord_gateway_ping_milliseconds{{{SEL}}})", 16, unit="ms")
stat("Playlists salvas", f"max(sabadaco_playlists_saved{{{SEL}}})", 20)
advance(4)

row("Música")
series("Áudio", [
    (f"sum(rate(sabadaco_audio_sent_bytes_total{{{SEL}}}[$__rate_interval]))", "enviado ao Discord"),
    (f"sum(rate(sabadaco_audio_downloaded_bytes_total{{{SEL}}}[$__rate_interval]))", "baixado das fontes"),
], 0, unit="Bps")
series("Músicas por hora", [
    (f"sum by (origin) (increase(sabadaco_tracks_played_total{{{SEL}}}[1h]))", "{{origin}}"),
    (f"sum(increase(sabadaco_tracks_failed_total{{{SEL}}}[1h]))", "falharam"),
], 12)
advance(8)
series("Fila por servidor", [(f"sum by (guild) (sabadaco_queue_size{{{SEL}}})", "{{guild}}")], 0)
series("Buscas por minuto", [
    (f"sum by (outcome) (rate(sabadaco_searches_total{{{SEL}}}[$__rate_interval])) * 60", "{{outcome}}"),
], 12)
advance(8)

row("Comandos")
series("Comandos por minuto", [
    (f"sum by (command) (rate(sabadaco_commands_seconds_count{{{SEL}}}[$__rate_interval])) * 60", "/{{command}}"),
], 0, stack=True)
series("Erros por minuto", [
    (f'sum by (outcome) (rate(sabadaco_commands_seconds_count{{{SEL}, outcome!="success"}}[$__rate_interval])) * 60',
     "{{outcome}}"),
], 12)
advance(8)
series("Tempo de resposta", [
    (f"histogram_quantile(0.95, sum by (le) (rate(sabadaco_commands_seconds_bucket{{{SEL}}}[$__rate_interval])))",
     "p95 comandos"),
    (f'max(sabadaco_interactions_latency_seconds{{{SEL}, quantile="0.99"}})', "p99 interações (2 min)"),
], 0, unit="s")
series("p95 por comando", [
    (f"histogram_quantile(0.95, sum by (le, command) (rate(sabadaco_commands_seconds_bucket{{{SEL}}}[$__rate_interval])))",
     "/{{command}}"),
], 12, unit="s")
advance(8)

row("JVM e container")
series("Memória", [
    (f'sum(container_memory_working_set_bytes{{{POD}, container!="", container!="POD"}})', "pod (working set)"),
    (f'max(kube_pod_container_resource_limits{{{POD}, resource="memory"}})', "limite do pod"),
    (f'sum(jvm_memory_used_bytes{{{SEL}, area="heap"}})', "heap usado"),
    (f'sum(jvm_memory_used_bytes{{{SEL}, area="nonheap"}})', "fora do heap (metaspace + code cache)"),
], 0, unit="bytes")
series("CPU", [
    (f'sum(rate(container_cpu_usage_seconds_total{{{POD}, container!="", container!="POD"}}[$__rate_interval]))',
     "pod (cores)"),
    (f'max(kube_pod_container_resource_limits{{{POD}, resource="cpu"}})', "limite"),
], 12)
advance(8)
series("Threads e GC", [
    (f"sum(jvm_threads_live_threads{{{SEL}}})", "threads"),
    (f"sum(rate(jvm_gc_pause_seconds_sum{{{SEL}}}[$__rate_interval])) * 1000", "pausa de GC (ms/s)"),
], 0)
series("MongoDB", [
    (f"sum by (command) (rate(mongodb_driver_commands_seconds_count{{{SEL}}}[$__rate_interval]))", "{{command}}/s"),
    (f"max(mongodb_driver_commands_seconds_max{{{SEL}}}) * 1000", "mais lento (ms)"),
], 12)
advance(8)

dashboard = {
    "uid": "sabadaco",
    "title": "Sabadaço · bot de música",
    "tags": ["sabadaco", "discord"],
    "timezone": "browser",
    "refresh": "30s",
    "time": {"from": "now-6h", "to": "now"},
    "schemaVersion": 39,
    "templating": {"list": [
        {"name": "datasource", "type": "datasource", "query": "prometheus", "label": "Fonte"},
        {"name": "namespace", "type": "custom", "query": "prd", "current": {"text": "prd", "value": "prd"},
         "label": "Namespace"},
    ]},
    "panels": [dict(p, id=i + 1) for i, p in enumerate(panels)],
}
OUT.write_text(json.dumps(dashboard, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"gerado: {OUT} ({len(panels)} painéis)")
