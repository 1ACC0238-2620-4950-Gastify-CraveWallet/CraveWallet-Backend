# Integración móvil para AV2

La revisión del 13 de noviembre de 2026 corresponde a
[Kotlin/Compose, `develop` en `e72d283`](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Mobile/tree/e72d283)
y [Flutter, `develop` en `ad7ace8`](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Flutter/tree/ad7ace8).
Ambas apps tienen un modo conectado que usa la API REST y un modo de demostración con datos locales.

| Flujo | Kotlin/Compose | Flutter |
| --- | --- | --- |
| Sesión | Registro, login, JWT, renovación, logout y lectura de perfil. | Registro, login, JWT, renovación, logout y lectura de perfil. |
| Suscripciones | Alta, listado, historial de canceladas, edición y cancelación. Usa la tasa del portafolio o `GET /exchange-rate`. | Mismas operaciones sobre suscripciones. Lee la tasa del portafolio. Si el backend indica que la conversión no está disponible, debe mostrar ese estado en vez de calcular con la tasa de demostración. |
| Resumen por categoría y ahorro al cancelar | Pendiente leer `monthlyTotalsByCategory` y `estimatedSavings`. | Pendiente leer esos campos. La gráfica calcula los totales por categoría en el cliente. |
| Delivery | Alta, resumen y presupuesto. Mantiene el `requestId` al reintentar el mismo gasto. | Alta, resumen y presupuesto. Debe conservar el UUID del formulario si el usuario reintenta un gasto tras un fallo. |
| Sugerencias de comercios | Pendiente conectar el campo de comercio. | Pendiente conectar el campo de comercio. |
| Premium | El modo conectado fija el plan en Free y muestra que Premium no está disponible. `fix/premium-price-9-99` corrige el precio en dos archivos y sigue fuera de `develop`. | El perfil remoto queda en Free. El modo de demostración usa un plan Premium local. |
| Recordatorios | El calendario se gestiona en Android y la prueba de integración cubre el contrato REST del recordatorio. | La pantalla cambia preferencias en memoria. Queda pendiente conectarla al calendario o al sistema de avisos si se incluye en el recorrido. |

## Configuración compartida

Las apps se conectan a la URL HTTPS del API. El servicio configura PostgreSQL,
Mercado Pago y Google Places en [Render](cloud-deployment.md). Ambas apps traen
`https://cravewallet-api.onrender.com` como valor predeterminado. Si el nuevo
despliegue conserva el dominio, se puede reutilizar ese valor. Si cambia, usa
la URL de Render hasta el dominio, pues los clientes agregan `/api/v1` al llamar.

```powershell
# Kotlin / Android
.\gradlew.bat :app:assembleDebug "-PAPI_BASE_URL=https://API_DEL_AV2"
# Flutter
flutter run --dart-define=API_BASE_URL=https://API_DEL_AV2
```

En Kotlin, el valor guardado en **Servidor de pruebas** tiene prioridad sobre
el valor compilado. Cuando el API se conecte a una base vacía, crea una cuenta
de demostración y vuelve a iniciar sesión en ambas apps.
Para Flutter Web servido desde otro origen, el backend necesitaría permitir
ese origen mediante CORS. Android y iOS usan el cliente HTTP nativo.

Para probar Flutter Web en la misma PC, inicia el backend con el perfil
`local` o `postgres` y ejecuta en el repositorio Flutter:

```powershell
flutter run -d chrome --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

Los perfiles de desarrollo admiten el origen HTTP de `localhost` o
`127.0.0.1` en el puerto que elija Flutter. Si el backend ya estaba
ejecutándose cuando cambió la configuración, reinícialo para aplicarla.

## Contratos para completar la integración

- [Premium](premium-api.md) describe estado, precio, checkout, cancelación e
  historial. La app abre `checkoutUrl` y vuelve a consultar `GET /premium/status`
  al regresar. El backend cambia el plan al verificar el pago. En Kotlin,
  el cliente debe admitir las respuestas de tipo arreglo para `GET /premium/payments`
  y sugerencias. Flutter ya envuelve esos arreglos bajo `items` en su cliente HTTP.
- [Delivery](delivery-api.md#sugerencias-de-comercios) documenta
  `GET /api/v1/delivery-expenses/merchants/suggestions?query=...` y el registro manual.
- [Suscripciones](subscriptions-api.md) describe `monthlyTotalsByCategory` y
  `estimatedSavings`, con los casos en que el importe en soles es `null`.

## Evidencia

- Kotlin conserva un [resultado de integración contra el despliegue TB1](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Mobile/blob/e72d283/docs/cloud-integration-results.json).
  Ocho pruebas de la suite Kotlin pasaron en la revisión local. La prueba
  adicional `realBackendRoundTrip` pasó con `CRAVE_LIVE_BACKEND` apuntando al JAR
  local del AV2 con H2. Ejercitó registro, CRUD de suscripciones, recordatorio,
  presupuesto, reintento de gasto, renovación de sesión y logout.
- El [CI de Flutter en `ad7ace8`](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Flutter/actions/runs/43304678864)
  ejecutó análisis estático y pruebas de modelos.
- Queda por ejecutar ambos clientes contra la URL pública del AV2, usando
  la misma cuenta de prueba para confirmar datos compartidos, y capturar el
  recorrido móvil con un pago de prueba y sugerencias reales.
