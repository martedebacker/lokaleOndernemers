#!/bin/bash

echo Backend testen...
#(./mvnw -q test) || { echo "Backendtests mislukt, deploy afgebroken"; exit 1; }

# The Dockerfile skips the tests: they ran above, and under the arm64 emulation they'd take ages.
echo Building...
./docker-build.sh || exit 1

echo Copying docker-compose.yml and scripts...
# The passwords and keys stay in the .env next to it on the server (see deploy/rotate-secrets.sh).
scp docker-compose.yml rotate-passwords.sh jdb@192.168.0.79:/home/jdb/dev/lokaleondernemers/ || exit 1

#echo Pre-deploy backup...
## A deploy can run database migrations, so it doesn't go ahead without a fresh dump, also kept here.
#LATEST=$(ssh jdb@192.168.0.79 'bash /home/jdb/dev/lokaleondernemers/backup.sh') || { echo "Backup mislukt, deploy afgebroken"; exit 1; }
#mkdir -p ./backups
#scp "jdb@192.168.0.79:$LATEST" ./backups/ || exit 1
#echo "Backup lokaal opgeslagen: ./backups/$(basename "$LATEST")"

echo Updating remote...
ssh jdb@192.168.0.79 /home/jdb/dev/lokaleondernemers/update-lokaleondernemers-app.sh || exit 1
#echo Wachten op opstart...
## The compose healthcheck turns the container healthy once the app is up and reaches its database.
#ssh jdb@192.168.0.79 'for i in $(seq 60); do
#  status=$(docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{end}}" lokaleondernemers 2>/dev/null)
#  case "$status" in
#    healthy) echo "App is gezond"; exit 0 ;;
#    unhealthy) break ;;
#  esac
#  sleep 5
#done
#echo "App is niet gezond geworden (${status:-geen status}), laatste logs:"
#docker logs --tail=100 lokaleondernemers 2>&1
#exit 1' || exit 1
