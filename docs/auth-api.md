# Autenticación y perfil — TS01

Implementación de US01, US02, US03 y US33, con las rutas de TS01 del informe.
Las respuestas no contienen contraseñas ni sus hashes. El propietario se obtiene
del JWT; el cliente no puede elegir otro usuario mediante el cuerpo de la solicitud.

| Método | Ruta | Respuesta correcta | Autenticación |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | 201, perfil y par de tokens | Pública |
| POST | `/api/v1/auth/login` | 200, perfil y par de tokens | Pública |
| POST | `/api/v1/auth/refresh` | 200, nuevo par de tokens | Token de renovación en el cuerpo |
| POST | `/api/v1/auth/logout` | 204, sin cuerpo | Bearer JWT |
| GET | `/api/v1/users/me` | 200, perfil propio | Bearer JWT |
| PATCH | `/api/v1/users/me` | 200, perfil actualizado | Bearer JWT |

## Registro y login

```json
{"email":"persona@example.com","password":"MiPassword123"}
```

El correo se normaliza a minúsculas. La política inicial de registro exige al
menos ocho caracteres, una letra y un número, y un máximo de 72 bytes UTF-8 por
la función BCrypt. El correo ya registrado responde 409. Formato inválido,
contraseña débil o cuerpo JSON inválido responden 400. Login con correo desconocido
o contraseña incorrecta responde 401 con el mismo mensaje.

Ejemplo de forma de respuesta; los valores entre corchetes son ilustrativos:

```json
{
  "accessToken": "[JWT firmado]",
  "refreshToken": "[token aleatorio de renovación]",
  "tokenType": "Bearer",
  "expiresAt": "[fecha ISO-8601 de vencimiento]",
  "user": {
    "id": "[UUID]",
    "email": "persona@example.com",
    "referenceCurrency": "PEN"
  }
}
```

La creación de cuenta y sesión se confirma en una transacción. Los hashes se
persisten en `iam_users` y `iam_sessions`; no se guarda el JWT completo.

## Acceso, renovación y cierre

El JWT de acceso dura una hora y valida firma HS256, issuer, audience, expiración
y sesión activa del propietario. Se envía en `Authorization: Bearer <accessToken>`.
El token de renovación contiene 32 bytes aleatorios, se guarda mediante SHA-256
y caduca a los 30 días. Son decisiones iniciales de configuración del backend.

Para renovar, enviar sin cabecera Bearer:

```json
{"refreshToken":"[token vigente]"}
```

Cada renovación bloquea el registro durante la transacción y revoca la sesión
anterior. Solo una de dos solicitudes concurrentes puede consumir el mismo token.
El cliente debe sustituir ambos tokens; repetir el anterior responde 401.
Logout revoca la sesión actual y, por tanto, su JWT y token de renovación. Otras
sesiones del mismo usuario siguen activas. No hay recuperación de contraseña ni
verificación de correo implementadas en esta versión.

Para confirmar la moneda de referencia:

```json
{"referenceCurrency":"PEN"}
```

Solo PEN es aceptado, conforme a US03. JWT falsificado, vencido, revocado o vinculado
a otro propietario responde 401. Las rutas de módulos no implementados siguen
cerradas. Los errores de validación/negocio usan `ProblemDetail`; no exponen valores
rechazados de contraseñas ni información SQL.

## Verificación y límites

`./mvnw verify` ejecuta pruebas con HTTP simulado y persistencia H2, incluida la
migración V1. Eso no certifica comportamiento específico de PostgreSQL ni
integración en un teléfono. PostgreSQL y despliegue remoto deben verificarse por
separado antes de registrarlos como evidencia en el informe.

Referencias: [TS01 del informe](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Report/blob/develop/docs/chapter_2.md),
[Spring Security: JWT Resource Server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
