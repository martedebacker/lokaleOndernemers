#!/usr/bin/env bash
# Genereert nieuwe databankwachtwoorden op de productieserver en past ze toe.
#
# Gebruik (in de map met docker-compose.yml):  ./rotate-passwords.sh
#
# Wat het doet:
#  1. start MariaDB (indien nodig) met de huidige wachtwoorden uit .env;
#  2. zet nieuwe wachtwoorden voor de app-gebruiker en root in MariaDB;
#  3. schrijft ze naar .env (de vorige versie wordt bewaard als .env.bak.<tijdstip>);
#  4. herstart de containers zodat de app de nieuwe wachtwoorden gebruikt.
#
# Bestaat .env nog niet, dan wordt het aangemaakt op basis van .env.example. Het beheerderswachtwoord
# wordt enkel gegenereerd als het nog niet ingevuld is: het geldt alleen bij de allereerste start
# en daarna wijzig je het in de app zelf.
set -euo pipefail

cd "$(dirname "$0")"

ENV_FILE=.env
PLACEHOLDER=VERANDER_MIJ

genereer() {
    # Enkel letters en cijfers: geen escaping nodig in .env, SQL of sed.
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32 || true
}

lees() { # lees KEY STANDAARD
    local waarde=""
    if [[ -f $ENV_FILE ]]; then
        waarde=$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)
    fi
    echo "${waarde:-$2}"
}

zet() { # zet KEY WAARDE
    if grep -qE "^$1=" "$ENV_FILE"; then
        sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
    else
        echo "$1=$2" >>"$ENV_FILE"
    fi
}

# Huidige waarden; de standaarden zijn dezelfde als in docker-compose.yml.
DB_USER=$(lees DB_USER lokaleondernemers)
OUD_ROOT=$(lees DB_ROOT_PASSWORD rootwachtwoord)

echo "MariaDB starten en wachten tot die gezond is..."
docker compose up -d --wait mariadb

NIEUW_DB=$(genereer)
NIEUW_ROOT=$(genereer)

echo "Wachtwoorden wijzigen in MariaDB..."
docker compose exec -T -e MYSQL_PWD="$OUD_ROOT" mariadb mariadb -uroot <<SQL
ALTER USER '$DB_USER'@'%' IDENTIFIED BY '$NIEUW_DB';
ALTER USER IF EXISTS 'root'@'%' IDENTIFIED BY '$NIEUW_ROOT';
ALTER USER IF EXISTS 'root'@'localhost' IDENTIFIED BY '$NIEUW_ROOT';
FLUSH PRIVILEGES;
SQL

if [[ -f $ENV_FILE ]]; then
    cp -p "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d-%H%M%S)"
elif [[ -f .env.example ]]; then
    cp .env.example "$ENV_FILE"
else
    : >"$ENV_FILE"
fi
chmod 600 "$ENV_FILE"

zet DB_PASSWORD "$NIEUW_DB"
zet DB_ROOT_PASSWORD "$NIEUW_ROOT"

NIEUW_ADMIN=""
ADMIN=$(lees APP_ADMIN_WACHTWOORD "")
if [[ -z $ADMIN || $ADMIN == "$PLACEHOLDER" ]]; then
    NIEUW_ADMIN=$(genereer)
    zet APP_ADMIN_WACHTWOORD "$NIEUW_ADMIN"
fi

echo "Containers herstarten met de nieuwe wachtwoorden..."
docker compose up -d --wait

echo
echo "Klaar. Nieuwe wachtwoorden staan in $ENV_FILE."
if [[ -n $NIEUW_ADMIN ]]; then
    echo "Wachtwoord eerste beheerder ($(lees APP_ADMIN_EMAIL admin@lokaleondernemers.be)): $NIEUW_ADMIN"
    echo "Dit geldt enkel als er nog geen beheerder bestaat; wijzig het na de eerste login."
fi
if grep -qE "^[^#]*$PLACEHOLDER" "$ENV_FILE"; then
    echo "Let op: $ENV_FILE bevat nog $PLACEHOLDER-waarden (bv. SMTP); vul die zelf in."
fi
