# Cotización y recordatorios

## Cotización

`GET /api/v1/exchange-rate?from=USD&to=PEN`, con Bearer JWT. Solo PEN y USD.
Respuesta: from, to, rate, updatedAt (fecha del proveedor), fetchedAt (consulta
del backend), stale, source y attributionUrl. También admite la tasa inversa
y monedas iguales; estas últimas usan 1 sin llamar al proveedor.

El adaptador usa el [endpoint Open Access de ExchangeRate-API](https://www.exchangerate-api.com/docs/free),
como la app actual. Traduce únicamente USD/PEN, sin redistribuir el catálogo del
proveedor. La interfaz móvil debe mostrar la atribución enlazada junto a las
conversiones. Es una estimación diaria, no una tasa garantizada de cobro.

Caché en memoria por instancia durante 24 horas; altas simultáneas comparten
la consulta. Tras un fallo espera 60 segundos antes de reintentar. Con dato previo
de menos de siete días devuelve stale=true y sus fechas originales; sin dato
utilizable responde 503. Siete días es una política provisional del proyecto.
Conexión limitada a tres segundos y solicitud a cinco segundos. Rechaza 429,
errores HTTP, JSON inválido, base distinta de USD, tasa no positiva y fechas
anómalas. Nunca inventa una tasa para mantener el dashboard.

El listado de suscripciones conserva monthlyTotalsByCurrency y agrega
monthlyTotalPen, conversionAvailable y exchangeRate. Si no hay cotización,
monthlyTotalPen=null y conversionAvailable=false; conserva importes originales.
Con dato antiguo indica exchangeRate.stale=true. El resumen respeta sus filtros.

La caché se pierde al reiniciar y no se comparte entre réplicas. Persistencia y
spikes de capacidad pendientes; no se afirma que estén ejecutados. El servidor
puede configurar `cravewallet.exchange.endpoint`; no es un parámetro del usuario.
Esta modalidad no necesita clave.

## Datos del recordatorio

`GET /api/v1/subscriptions/{id}/reminder`, con Bearer JWT. Devuelve title,
description, reminderAt, billingAt y timeZone. Recurso ajeno o inexistente: 404;
cancelado: 409. Calcula 24 horas antes de renovar a las 00:00 en America/Lima,
convención inicial porque el modelo guarda fecha sin hora. No implica conocer
la hora real de cobro. Android debe comprobar si el aviso sigue siendo futuro.

No escribe en Google Calendar ni solicita permisos ni envía push. El cliente
debe integrar CalendarContract/WorkManager y manejar cambios o cancelaciones.

## Verificación

Pruebas de caché, expiración, caída con/sin dato previo, retroceso, concurrencia,
tasa inversa, fecha inválida, traducción HTTP y rechazo de 429. Las pruebas
automatizadas no dependen de Internet: mocks y servidor HTTP local. API con JWT
verifica autorización, aislamiento, cancelación y cotización.
También se probaron Swagger y el proveedor real contra el backend local, con
cuentas ficticias y datos de prueba. PostgreSQL y Android pendientes.
