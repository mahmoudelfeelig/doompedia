# Hetzner Shared Caddy Deploy

This project is deployed as an internal static container behind the shared
`/opt/caddy` reverse proxy. Only the shared Caddy container owns host ports 80
and 443.

Recommended server layout:

```text
/opt/doompedia/
  web/
    media/featured/
    packs/
  deploy/Caddyfile
  deploy/Caddyfile.public
  deploy/docker-compose.prod.yml
  deploy/.env
```

Publish data locally first:

```bash
BASE_URL="https://doompedia.elfeel.me/packs/en-core-1m/v1" ./scripts/publish_pack.sh
```

Then copy:

```bash
rsync -av --delete web/ user@server:/opt/doompedia/web/
rsync -av --delete data/site/packs/ user@server:/opt/doompedia/web/packs/
rsync -av deploy/ user@server:/opt/doompedia/deploy/
```

Create the server-only env file:

```bash
cp /opt/doompedia/deploy/.env.example /opt/doompedia/deploy/.env
nano /opt/doompedia/deploy/.env
```

Default values:

```env
DOOMPEDIA_DOMAIN=doompedia.elfeel.me
DOOMPEDIA_INTERNAL_PORT=8080
DOOMPEDIA_MEMORY_LIMIT=256m
DOOMPEDIA_CPU_LIMIT=0.50
PUBLIC_CADDY_DIR=/opt/caddy
```

`web/media/featured` contains the 500-image starter set and is included by the
first command. The generated set currently occupies about 110 MB. Android
stores only its manifest and caches viewed images locally.

Start the private static container:

```bash
cd /opt/doompedia
docker compose -f deploy/docker-compose.prod.yml up -d
```

Add the route from `deploy/Caddyfile.public` to `/opt/caddy/Caddyfile`, then
reload shared Caddy:

```bash
cd /opt/caddy
docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile
```

The Android defaults expect:

```text
https://doompedia.elfeel.me/packs/en-core-1m/v1/manifest.json
https://doompedia.elfeel.me/media/featured/<thumbnail-file>
```

Use the same path pattern for focused packs.
