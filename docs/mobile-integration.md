# Integración con CraveWallet-Mobile

Revisión del repositorio privado `CraveWallet-Mobile`, rama `develop`, commit
`2d4200e`. Sus archivos se revisaron sin modificar la aplicación ni ejecutar su
build Android.

## Lo que existe en la app

- Kotlin, Jetpack Compose y Material 3; no Flutter.
- Pantallas de Inicio, Gastos, alta, Análisis, Perfil y demostración de Premium.
- Suscripciones y perfil locales en JSON mediante SharedPreferences.
- Calendario mediante CalendarContract y avisos locales mediante WorkManager.
- Consulta de cotización al proveedor `open.er-api.com` directamente desde Android.
- Premium simulado mediante un cambio de estado local, sin cobros reales.

No se encontró un cliente para el backend, ni login/registro con sus endpoints.
La existencia del código no acredita instalación en un dispositivo ni todas las
historias satisfechas. WorkManager genera notificaciones locales; llamarlas push
remoto sería impreciso.

## Próximo contrato a conectar

Usar las rutas de [autenticación y perfil](auth-api.md). El cliente necesita
pantallas de registro/login, un cliente HTTP, almacenamiento protegido de tokens,
renovación y logout remoto. No se debe enviar un `userId` elegido por la interfaz
para obtener datos ajenos. El JWT identifica al propietario.

En el emulador de Android, el host de desarrollo normalmente se alcanza mediante
`http://10.0.2.2:8080`. Para un teléfono físico, se requiere una dirección accesible
del host o un despliegue HTTPS; `localhost` del teléfono apunta al propio teléfono.
La configuración actual del backend escucha solo en el equipo local.

## Diferencias que deben acordarse para integrar el CRUD

| Tema | App actual | Diseño del informe |
| --- | --- | --- |
| Stack móvil | Kotlin / Compose | Flutter / Dart |
| Monedas de suscripción | PEN, USD, EUR | PEN y USD |
| Periodicidades | Mensual, trimestral, anual | Mensual y anual |
| Persistencia móvil | SharedPreferences con JSON | SQLite como caché de lectura |
| Premium | Estado local de demostración | Acceso confirmado por backend y Stripe |
| Anticipación de avisos | Opciones configurables | TS04 y US12 describen 24 horas |

No se ampliaron silenciosamente los contratos para cubrir estas diferencias.
El equipo debe actualizar el informe o ajustar la app de forma coherente. El
backend de autenticación es independiente de Flutter/Kotlin y puede atender a
ambos clientes. El [CRUD de suscripciones](subscriptions-api.md) ya está disponible,
con PEN/USD y ciclos mensual/anual. Delivery, Premium y la conexión Android siguen
pendientes de implementar.
