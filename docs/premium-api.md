# Premium y facturación recurrente

## Decisión del Sprint 2

El equipo autorizó sustituir Stripe por Mercado Pago para el incremento del Sprint 2. Las referencias del Sprint 1 se conservan como antecedentes. US21 mantiene el precio de **S/ 9.99 PEN por mes**, US22 cubre la contratación, US23 la cancelación de futuras renovaciones y US31 el historial. TS06 usa la API de Suscripciones y webhooks de Mercado Pago. SP05 registra la decisión técnica descrita aquí. SP06 tiene un flujo reproducible de prueba y queda pendiente la evidencia de una ejecución real con Mercado Pago.

El flujo alojado usa `POST /preapproval` en estado `pending` con frecuencia mensual y precio fijo definido por el backend. La variante oficial con `preapproval_plan_id` exige un `card_token_id` y estado `authorized` al crear la suscripción. El checkout alojado con pago pendiente permite que el proveedor reciba los datos de la tarjeta directamente. El backend devuelve la URL `init_point` de esa operación para que la app la abra en el navegador. [Suscripciones con pago pendiente](https://www.mercadopago.com.pe/developers/es/docs/subscriptions/integration-configuration/subscription-no-associated-plan/pending-payments) · [Suscripciones con plan asociado](https://www.mercadopago.com.pe/developers/es/docs/subscriptions/integration-configuration/subscription-associated-plan)

## Contrato REST

Base `/api/v1/premium`. Usa `Authorization: Bearer <accessToken>` en las rutas de usuario. Las operaciones de checkout y cancelación se envían sin cuerpo. El propietario proviene del JWT y el correo de checkout se obtiene del perfil IAM.

| Método y ruta | Respuesta | Errores habituales |
| --- | --- | --- |
| GET `/status` | 200 con plan efectivo, precio, moneda, vigencia y cancelación programada localmente | 401 sin JWT |
| POST `/checkout` | 200 con `sessionId`, `checkoutUrl`, `status: PENDING`, `amount` y `currency` | 401 sin JWT, 409 si existe un período vigente o una contratación en curso sin enlace reutilizable, 503 si el proveedor o la configuración no confirman la operación |
| POST `/cancel` | 200 con la misma estructura de estado tras confirmar la cancelación remota | 401 sin JWT, 404 sin suscripción, 409 si el checkout aún carece de identificador remoto, 503 si no se puede comprobar el resultado |
| GET `/payments` | 200 con arreglo de pagos aprobados y rechazados verificados, ordenados por fecha descendente | 401 sin JWT |
| POST `/webhook?data.id=<id>` | 200 para entregas válidas procesadas, repetidas o ajenas a la integración | 400 si el JSON es inválido o no coincide con el ID firmado, 401 con firma inválida, 503 si falta configuración o falla una comprobación temporal |

Ejemplo de respuesta de checkout en Swagger. El valor real de `checkoutUrl` viene de Mercado Pago.

```json
{
  "sessionId": "abc123",
  "checkoutUrl": "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=abc123",
  "status": "PENDING",
  "amount": 9.99,
  "currency": "PEN"
}
```

Ejemplo de estado inmediatamente después de iniciar el checkout.

```json
{
  "plan": "FREE",
  "premium": false,
  "membershipStatus": "PENDING",
  "periodEndsAt": null,
  "cancelAtPeriodEnd": false,
  "monthlyPrice": 9.99,
  "currency": "PEN"
}
```

Ejemplo de elemento del historial. Los intentos rechazados tienen `paidAt: null` y se fechan con `recordedAt`.

```json
{
  "externalId": "123456789",
  "invoiceId": "321456789",
  "amount": 9.99,
  "currency": "PEN",
  "status": "approved",
  "paidAt": "2026-11-12T15:00:00Z",
  "recordedAt": "2026-11-12T15:00:05Z"
}
```

## Integración con Kotlin y Flutter

1. Usa `GET /status` para presentar el precio, el plan y la vigencia. Un usuario nuevo tiene `membershipStatus: null`.
2. Llama a `POST /checkout` y abre `checkoutUrl` en el navegador. Mantén el estado pendiente cuando el usuario regrese. Consulta `GET /status` al volver a la app y permite actualizar mientras espera el cobro. Usa `premium` para habilitar las funciones del plan.
3. Tras `POST /cancel`, muestra `periodEndsAt` como fin del acceso cuando `premium` siga activo. Si el backend devuelve 503, permite repetir la cancelación. Muestra `GET /payments` solo dentro de la sesión del usuario.

## Configuración y despliegue en Render

Configura estas variables privadas en el servicio del backend, junto con los valores existentes de PostgreSQL y JWT.

| Variable | Valor |
| --- | --- |
| `MERCADOPAGO_ACCESS_TOKEN` | Access Token de la integración en el entorno **Prueba** |
| `MERCADOPAGO_WEBHOOK_SECRET` | Clave secreta de firma del webhook del entorno de prueba |
| `MERCADOPAGO_BACK_URL` | URL HTTPS de retorno controlada por el equipo |
| `MERCADOPAGO_EXPECTED_LIVE_MODE` | Booleano esperado en el pago consultado. Por defecto `false`; el perfil local usa `true` para el flujo verificado con seller/buyer de prueba. No identifica por sí solo si las cuentas son reales. |

En Render, comprueba que el servicio de prueba despliegue el commit que contiene V5. Flyway aplica V5 sobre V1–V4. Confirma el arranque en `/actuator/health` y revisa `/v3/api-docs` o `/swagger-ui/index.html`. En el panel del proveedor configura la URL HTTPS pública terminada en `/api/v1/premium/webhook` y habilita los eventos de Suscripciones. Los tópicos procesados son `subscription_preapproval`, `subscription_authorized_payment` y `payment` cuando existe una factura recurrente vinculada. [Configuración de credenciales](https://www.mercadopago.com.pe/developers/es/docs/subscriptions/additional-content/your-integrations/credentials) · [Webhooks](https://www.mercadopago.com.pe/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks)

## Verificación y vigencia

El adaptador REST usa la API del proveedor con Access Token y conserva en el dominio únicamente los campos necesarios. El webhook valida HMAC-SHA256 a partir de `data.id` de la URL, `x-request-id` y `ts` de `x-signature`. Admite segundos y milisegundos, con una ventana de 24 horas hacia atrás y cinco minutos hacia adelante. Contrasta el identificador firmado con el cuerpo y consulta la suscripción, la factura y el pago correspondiente. La autorización de la suscripción y el retorno del navegador dejan la membresía sin acceso hasta que se verifique un pago aprobado en PEN de S/ 9.99 cuyo `live_mode` coincida con la configuración. Una discrepancia devuelve 503 con el nombre de la variable, sin marcar la entrega como procesada. Una entrega antigua marcada sin pago aprobado puede verificarse otra vez mediante Replay; las restricciones de pago y factura evitan duplicar el acceso.

En la prueba observada con seller y buyer de prueba, el pago devolvió `live_mode: true`. Mercado Pago documenta que las pruebas de Suscripciones usan las credenciales de producción del seller de prueba; no se debe inferir el tipo de cuenta únicamente de `live_mode` o del prefijo del token. [Pruebas de integración](https://www.mercadopago.com.mx/developers/en/news/2023/11/16/Questions-on-how-to-test-your-integration--).

El perfil `local` guarda H2 en `./.local-data/cravewallet` (directorio excluido de Git). Ejecuta siempre desde la raíz del proyecto para reutilizar la misma base. Las pruebas tienen su propia configuración H2 en memoria. Los datos de una instancia antigua en memoria no se migran automáticamente: deben exportarse antes de detenerla. Para esta sesión se guardó `before-premium-fix.sql` y se importó a la base de disco, conservando el usuario y el checkout pendiente.

V5 guarda las contrataciones, los pagos y las entregas procesadas vinculadas a una operación local. La referencia externa correlaciona el checkout con su propietario. Las transacciones serializan escrituras por usuario y las restricciones únicas deduplican los pagos y las facturas aprobadas. La vigencia se calcula desde `debit_date` de la factura más un mes calendario en Lima. Un evento atrasado solo puede extender el acceso si su período vence más tarde que el período guardado.

Para cancelar, el backend envía `status: canceled` cuando corresponde y consulta de nuevo la suscripción para confirmar el resultado. El acceso local pagado se conserva hasta el vencimiento. Una factura con fecha de débito a partir de la cancelación puede quedar en el historial si se confirma un pago, pero no amplía la vigencia. [Referencia de Suscripciones, facturas y pagos](https://www.mercadopago.com.pe/developers/es/reference/online-payments/subscriptions/overview) · [Gestión de suscripciones](https://www.mercadopago.com.pe/developers/es/docs/subscriptions/subscription-management)

## Prueba reproducible con Mercado Pago

1. Crea o selecciona la integración de Suscripciones en el panel de Mercado Pago Perú. Configura el Access Token de prueba y la clave de webhook de ese entorno. Despliega el backend de prueba con la URL de retorno.
2. Registra una cuenta de CraveWallet con el correo del comprador de prueba del proveedor. En Swagger ejecuta `POST /api/v1/auth/login` y usa el token en **Authorize**. Consulta `GET /api/v1/premium/status`.
3. Ejecuta `POST /api/v1/premium/checkout` dos veces y confirma que se devuelve la misma sesión pendiente. Abre el enlace y completa la [compra de prueba oficial](https://www.mercadopago.com.pe/developers/es/docs/subscriptions/integration-test/payment-approval) con una tarjeta de prueba de Perú.
4. Cuando Mercado Pago entregue el webhook de pago, confirma que `GET /status` pasa a Premium y `GET /payments` contiene el pago por S/ 9.99. Repite con otro comprador y un resultado rechazado para comprobar que la cuenta sigue Free.
5. Cancela con `POST /cancel` y verifica el estado remoto en el panel. Comprueba que el período local conserva la fecha pagada. Verifica en el siguiente ciclo que Mercado Pago no programa ni cobra la renovación. Conserva evidencia de ambos sistemas con credenciales y datos de prueba excluidos.

En local, ejecuta `./mvnw.cmd clean verify` con JDK 21. La suite usa H2, MockMvc, un puerto simulado y mocks HTTP. Puede ejecutarse sin credenciales.

## Límites de la verificación

- Un error de red al crear la suscripción puede dejar el resultado remoto indeterminado. Al reintentar, el backend busca por correo y valida la coincidencia exacta de `external_reference`. Un webhook auténtico también puede reconciliar la reserva. Si ninguna consulta confirma el resultado, el backend mantiene la reserva para evitar otro cobro y se requiere reconciliación operativa en el panel.
- La prueba de cancelación con mocks verifica el PUT, el GET de confirmación y el vencimiento local. Falta observar en Mercado Pago el siguiente ciclo y posibles cobros ya iniciados para acreditar US23 de extremo a extremo.
- La revocación de períodos ya concedidos por contracargos o reembolsos, las alertas operativas y la recuperación automática de notificaciones perdidas requieren trabajo posterior.
