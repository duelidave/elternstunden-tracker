# Lokal ausprobieren

Demo-Abläufe für den lokalen Stack aus dem [README](../README.md) (`docker compose up` und `npm run dev`). Alle Konten, Passwörter und Secrets hier sind Beispielwerte aus `.env.example` und gelten nur lokal.

Die App öffnest du immer über den Dev-Server (http://localhost:5173), auch die Links aus den Mails zeigen dorthin.
Am besten mit Chrome oder Firefox: Beide akzeptieren das `Secure`-Session-Cookie auch auf `http://localhost`.

- **Registrieren:** `/register` ausfüllen, die Mail kommt in Mailpit (http://localhost:8025) an. Link öffnen, "Jetzt anmelden".
- **Anmelden:** `/login`, E-Mail eingeben, Link aus Mailpit öffnen.
- **Rollen wechseln (D-26):** Die Dev-Leiste unten meldet ohne Mail direkt als Familie, Partner, Unzugeordnet, Admin oder Master an.
  Sie gibt es nur mit `npm run dev`, nie im Produktions-Build.
- **Master-Login:** `/login/master` mit `master@dev.local` und dem Passwort `local-dev-master`. Den Code liefert eine
  Authenticator-App mit dem Secret `MASTER_ADMIN_TOTP_SECRET` aus `.env`, oder `oathtool --totp -b <secret>`.
- **Automatische Zuordnung:** Registriere dich mit dem Kind "Anna Müller", geboren am 03.05.2017. Nach dem Klick auf den
  Link (im selben Browser) bist du der "Familie Müller" zugeordnet. Mit einem Tippfehler landest du auf dem Wartescreen.
- **Mit Google anmelden:** Lokal steht ein OIDC-Mock (http://localhost:8090) für Google. Auf seiner Anmeldeseite
  gibst du als „user/subject" einfach eine Adresse ein, etwa `anna@gmail.com`. Neue Adressen landen auf „Noch ein
  Schritt", bekannte melden sich direkt an. Nur `@gmail.com`-Adressen werden mit einem bestehenden Konto verknüpft
  (7.1.5); andere verknüpfst du im Profil.
- **Mit netID anmelden (GMX, WEB.DE):** Der OIDC-Mock steht lokal auch für netID (Issuer `/netid`). Als Nutzer eine
  Adresse wie `anna@gmx.de` eingeben. GMX- und WEB.DE-Adressen werden mit einem bestehenden Konto verknüpft, andere
  nur über das Profil. Für das echte netID braucht es `NETID_CLIENT_ID` und `NETID_CLIENT_SECRET` aus dem
  netID-Entwicklerportal und eine HTTPS-Adresse.
- **Passkeys:** Im Profil „Passkey hinzufügen“ (direkt nach der Anmeldung, sonst erst neu anmelden). Danach auf
  `/login` „Mit Passkey anmelden“. Lokal geht das in Chrome über den Passwortmanager, ein Handy per QR-Code oder in
  den DevTools unter „WebAuthn“ mit einem virtuellen Authenticator.
- **Zweiter Faktor für Admins:** Im Profil „Einrichten", QR-Code mit einer Authenticator-App scannen. Danach fragt die
  Anmeldung per E-Mail-Link oder Google nach dem Code. Die Dev-Leiste überspringt ihn (D-26).
- **Admin-Bereich:** Über die Dev-Leiste als Admin anmelden, dann "Zum Admin-Bereich". Die Übersicht zeigt
  "Uli Unzugeordnet" mit dem Vorschlag "Mila Muster" zum Zuordnen. Dort gibt es auch Kinder, Familien, Nutzer und
  Schuljahre, inklusive CSV-Import.
- **Stunden eintragen:** Als Familie anmelden. Die Startseite zeigt den Fortschritt von "Familie Demo" (zwei bestätigte
  Einträge, einer wartet). "Stunden eintragen" öffnet das Formular: Dauer-Chip, Text, "Eintragen". "Andere Dauer"
  wechselt zu Uhrzeit und 15-Minuten-Schritten. Offene Einträge lassen sich ändern und löschen.
- **Übersicht:** Auf jeder Admin-Seite steht oben, wie viele Familien im Schuljahr noch Stunden offen haben. Ein Klick
  zeigt die Liste unter "Familien": Suche, Schuljahr, "Nur Familien mit fehlenden Stunden", sortierbare Spalten.
- **Abrechnung:** Als Admin unter "Abrechnung" das PDF für alle Familien oder eine Familie herunterladen. Im laufenden
  Schuljahr ist es ein Zwischenstand, danach die Abschlussabrechnung. Im Familiendetail gibt es den PDF-Link direkt.
- **Bestätigen:** Als Admin unter "Zu bestätigen" einzeln, alle einer Familie oder eine Auswahl bestätigen. Die Zahl
  offener Einträge steht in der Navigation und auf der Übersicht. Im Familiendetail können Admins Einträge korrigieren
  und für die Familie eintragen.
