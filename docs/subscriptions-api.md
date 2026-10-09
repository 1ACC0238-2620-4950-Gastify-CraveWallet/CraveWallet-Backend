# Contrato de suscripciones

Todas las rutas requieren `Authorization: Bearer <accessToken>`. El propietario
se obtiene del JWT; no existe un parámetro `userId` para seleccionar otra cuenta.
Una suscripción ajena devuelve 404 tanto al consultar como al editar o cancelar.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/subscriptions` | 201 y Location; registrar |
| GET | `/api/v1/subscriptions` | 200; portafolio activo por defecto |
| GET | `/api/v1/subscriptions/{id}` | 200; detalle propio |
| PATCH | `/api/v1/subscriptions/{id}` | 200; modificar importe, categoría o fecha |
| POST | `/api/v1/subscriptions/{id}/cancel` | 200; cancelación local idempotente |
| GET | `/api/v1/subscriptions/{id}/reminder` | 200; datos para el aviso del dispositivo |

## Alta

```json
{
  "name": "Netflix",
  "amount": 29.90,
  "currency": "PEN",
  "category": "Streaming",
  "billingCycle": "MONTHLY",
  "nextBillingDate": "2026-11-08"
}
```

Nombre obligatorio, hasta 100 caracteres; categoría libre obligatoria, hasta 60.
Importe decimal entre 0 y 9999999999.99, con hasta dos decimales. Se admite cero
para registros gratuitos, conforme al valor monetario no negativo del diseño.
Monedas: `PEN` y `USD`; ciclos: `MONTHLY` y `ANNUAL`. La fecha no puede ser anterior
a hoy en `America/Lima`, convención inicial explícita para el público peruano.
No implica un horario de cobro ni una cotización garantizada.

La respuesta agrega `id`, `status: ACTIVE` y `cancelledAt: null`; no expone el
propietario. No hay catálogo remoto: una selección del catálogo móvil puede
enviar este mismo contrato con los campos precompletados.

## Listado y resumen

Devuelve items, monthlyTotalsByCurrency, monthlyTotalPen, conversionAvailable
y exchangeRate. Ver [cotización y recordatorios](exchange-and-reminders-api.md).
Filtros opcionales: `status=ACTIVE|CANCELLED`, `search` sobre el nombre y `category`
exacta, sin distinguir mayúsculas. Orden: fecha de renovación y UUID. El resumen
corresponde a los resultados filtrados activos. Anuales se dividen entre doce;
la suma se redondea a dos decimales. Es una estimación mensual, no un historial
de cargos pagados. El desglose separa monedas y monthlyTotalPen convierte USD con
cotización fechada. Si no está disponible conserva los originales y devuelve
total PEN null. El historial cancelado tiene desglose vacío y total cero.

## Edición y cancelación

PATCH acepta cualquier combinación no vacía de `amount`, `category` y
`nextBillingDate`; `null` equivale a omitir el campo. Ejemplo:

```json
{ "amount": 35.90, "nextBillingDate": "2026-12-08" }
```

Una cancelación conserva importe, fecha y registro, fija `cancelledAt` y excluye
el elemento del portafolio activo. Repetirla devuelve el mismo registro y fecha.
Editar un cancelado devuelve 409; no hay reactivación ni DELETE. La cancelación
solo afecta CraveWallet: no cancela el servicio contratado con el proveedor.

## Plan inicial y límites

El backend aplica cinco suscripciones activas por usuario como baseline Free,
coherente con la app actual. Bloquea el registro número seis con 409; cancelar
libera un cupo. El bloqueo transaccional del propietario serializa altas
simultáneas. Edición y cancelación bloquean el registro para evitar sobrescrituras
simultáneas. Todas las cuentas usan esta política mientras Premium no exista;
el flag Premium simulado en Android no autoriza excepciones. El equipo debe
confirmar las reglas de Premium antes de implementar facturación.

Errores: 400 campos/JSON inválidos, 401 sin token válido, 404 recurso inexistente
o ajeno y 409 límite o edición de cancelados. Flyway V2 crea tabla e índice;
no se almacena dinero usando float/double.

## Integración pendiente y verificación

La app Kotlin deberá mapear `MENSUAL` a `MONTHLY`, `ANUAL` a `ANNUAL` y sus estados
locales a ACTIVE/CANCELLED. EUR y trimestral siguen fuera del contrato del informe.
No se modificó ni conectó Android. Cotización, datos del recordatorio y registro/
presupuesto de Delivery ya existen. Calendario móvil y cobros Premium pendientes.

`SubscriptionIntegrationTest` verifica aislamiento entre cuentas, validaciones,
edición, historial, cancelación repetida, reutilización de cupo, filtros, totales
por moneda y dos altas concurrentes sobre el último cupo. Se ejecuta con H2,
migraciones reales y JWT reales de registro. Falta validar PostgreSQL y la app.
