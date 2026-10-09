# Delivery: gastos y presupuesto

Tres endpoints requieren Bearer JWT; el propietario viene del token. Todos los
importes son PEN. Flyway V3 crea gastos y presupuestos, con restricciones únicas
por cuenta/solicitud y cuenta/año/mes.

## Registro

`POST /api/v1/delivery-expenses`

```json
{
  "requestId": "4fac83e3-8326-4e4d-bcf6-c5c84690ce0a",
  "merchant": "Restaurante",
  "amount": 35.50,
  "category": "Comida",
  "expenseDate": "2026-10-08"
}
```

201 con id, requestId, comercio, importe, moneda, categoría y fecha. Comercio
obligatorio hasta 100 caracteres; categoría libre obligatoria hasta 60. Importe
positivo, hasta diez dígitos enteros y dos decimales. Fecha desde 1900 hasta hoy
en America/Lima. Cada gasto nuevo debe tener un UUID de solicitud distinto.
Conservar el UUID al reintentar: mismos campos normalizados devuelve 200 y el
registro anterior; contenido distinto con ese UUID devuelve 409. El UUID se
limita a la cuenta, no se comparte entre usuarios.

Gasto y acumulado se actualizan en una transacción; un bloqueo del propietario
protege altas simultáneas y reintentos. No se suma de nuevo mediante un consumidor.

## Presupuesto

`PUT /api/v1/delivery-expenses/budget`

```json
{ "year": 2026, "month": 10, "spendingLimit": 300.00 }
```

200 con año, mes, moneda, límite, acumulado, saldo y exceeded. Año 1900–9999,
mes 1–12; límite positivo con dos decimales. null quita el límite conservando
el acumulado y los gastos. Saldo negativo indica exceso. Igualar el límite no
lo supera; superarlo no bloquea el registro ni pedidos en plataformas externas.

## Resumen

`GET /api/v1/delivery-expenses/summary?year=2026&month=10`

Sin parámetros o con month=actual utiliza el mes actual en America/Lima.
Para otro período enviar año y mes numérico juntos. 200 con total, spendingLimit,
remaining, exceeded, totalsByCategory y totalsByWeek. Sin límite, límite y saldo
son null y exceeded es false. Las semanas corresponden a días 1–7, 8–14, 15–21,
22–28 y 29–fin de mes. No son semanas ISO. Total y desgloses proceden de una
consulta de gastos del mismo usuario y período; no mezclan datos ajenos.

## Eventos y alcance

El alta publica eventos internos ExpenseRegistered y, al pasar de no excedido
a excedido, LimitExceeded. Consumidores de efectos externos deben usar
`@TransactionalEventListener(AFTER_COMMIT)`. No hay avisos enviados ni outbox;
cambiar el límite actualiza el estado sin enviar una notificación.

Google Places, edición/eliminación, historial paginado y conexión Android están
pendientes. El comercio manual funciona sin integración externa. Las pruebas
verifican presupuesto, períodos, aislamiento, validaciones, deduplicación,
conflictos y altas simultáneas con un reintento. H2 verificado; PostgreSQL pendiente.
