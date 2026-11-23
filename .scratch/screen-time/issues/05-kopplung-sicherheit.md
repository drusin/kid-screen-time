# Wie werden Geräte gekoppelt und der Server abgesichert?

Type: grilling
Status: open
Blocked by: -

## Question

Der Server ist aus dem Internet erreichbar. Wie koppeln sich Kindgerät und Elterngeräte, und wie wird verhindert, dass Fremde oder das Kind Eltern-Aktionen ausführen? Vorschlag aus dem Charting: QR-Code-Kopplung aus einer Eltern-Ansicht, Geräte-Token pro Gerät, keine Benutzerkonten. Offen sind: Wie wird das **erste** Elterngerät gekoppelt (Bootstrap-Secret per Env-Variable?), wie werden Geräte entfernt bzw. widerrufen, was passiert bei Geräteverlust, und reicht das gegen Brute-Force und Fremdzugriff (Rate-Limits, Token-Länge)?
