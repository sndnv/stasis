package stasis.identity.api

import java.time.Instant

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives

import stasis.identity.RouteTest
import stasis.identity.api.oauth.OAuthFixtures
import stasis.identity.api.oauth.directives.AudienceExtraction
import stasis.identity.model.Generators
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.secrets.Secret
import stasis.layers

class OAuthSpec extends RouteTest with OAuthFixtures {
  "OAuth routes" should "handle code authorization requests" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val state = layers.Generators.generateString(withSize = 16)
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/authorization" +
        s"?response_type=code" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)
      stores.codes.all.await.headOption match {
        case Some(storedCode) =>
          storedCode.challenge should be(None)

          headers should contain(
            model.headers.Location(
              Uri(
                s"${client.redirectUri}" +
                  s"?code=${storedCode.code.value}" +
                  s"&state=$state" +
                  s"&scope=$scope"
              )
            )
          )

        case None =>
          fail("Unexpected response received; no authorization code found")
      }
    }
  }

  they should "handle PKCE code authorization requests" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val state = layers.Generators.generateString(withSize = 16)
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"
    val storedChallenge = StoredAuthorizationCode.Challenge(
      value = layers.Generators.generateString(withSize = 128),
      method = None
    )

    val uri =
      s"/authorization" +
        s"?response_type=code" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state" +
        s"&code_challenge=${storedChallenge.value}"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)
      stores.codes.all.await.headOption match {
        case Some(storedCode) =>
          storedCode.challenge should be(Some(storedChallenge))

          headers should contain(
            model.headers.Location(
              Uri(
                s"${client.redirectUri}" +
                  s"?code=${storedCode.code.value}" +
                  s"&state=$state" +
                  s"&scope=$scope"
              )
            )
          )

        case None =>
          fail("Unexpected response received; no authorization code found")
      }
    }
  }

  they should "handle token authorization requests" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"
    val state = layers.Generators.generateString(withSize = 16)

    val uri =
      s"/authorization" +
        s"?response_type=token" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)

      headers.find(_.is("location")) match {
        case Some(header) =>
          val headerValue = header.value()
          headerValue.contains("access_token") should be(true)
          headerValue.contains("token_type=bearer") should be(true)
          headerValue.contains("expires_in=30") should be(true)
          headerValue.contains(s"state=$state") should be(true)
          headerValue.contains(s"scope=$scope") should be(true)

        case None =>
          fail(s"Unexpected response received; no location found in headers [$headers]")
      }
    }
  }

  they should "reject authorization requests with invalid response types" in withRetry {
    val (_, _, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val responseType = "some-response"

    val uri =
      s"/authorization" +
        s"?response_type=$responseType"

    Get(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        s"The request includes an invalid response type: [$responseType]"
      )
    }
  }

  they should "handle authorization code token grants (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedCode = StoredAuthorizationCode(
      code = code,
      client = client.id,
      owner = owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}")
    )

    val uri =
      s"/token" +
        s"?grant_type=authorization_code" +
        s"&code=${code.value}" +
        s"&client_id=${client.id}"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(storedCode).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle authorization code token grants (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedCode = StoredAuthorizationCode(
      code = code,
      client = client.id,
      owner = owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}")
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(storedCode).await

    Post(
      "/token",
      FormData(
        "grant_type" -> "authorization_code",
        "code" -> code.value,
        "client_id" -> client.id.toString
      )
    ).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle PKCE authorization code token grants (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedChallenge = StoredAuthorizationCode.Challenge(
      value = layers.Generators.generateString(withSize = 128),
      method = None
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      client = client.id,
      owner = owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}"),
      challenge = Some(storedChallenge),
      created = Instant.now()
    )

    val uri =
      s"/token" +
        s"?grant_type=authorization_code" +
        s"&code=${code.value}" +
        s"&client_id=${client.id}" +
        s"&code_verifier=${storedChallenge.value}"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(storedCode).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle PKCE authorization code token grants (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedChallenge = StoredAuthorizationCode.Challenge(
      value = layers.Generators.generateString(withSize = 128),
      method = None
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      client = client.id,
      owner = owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}"),
      challenge = Some(storedChallenge),
      created = Instant.now()
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(storedCode).await

    Post(
      "/token",
      FormData(
        "grant_type" -> "authorization_code",
        "code" -> code.value,
        "client_id" -> client.id.toString,
        "code_verifier" -> storedChallenge.value
      )
    ).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle client credentials token grants (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val scope = s"${AudienceExtraction.UrnPrefix}:${client.id}"

    val uri =
      s"/token" +
        s"?grant_type=client_credentials" +
        s"&scope=$scope"

    stores.clients.put(client).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("scope") should be(true)
    }
  }

  they should "handle client credentials token grants (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val scope = s"${AudienceExtraction.UrnPrefix}:${client.id}"

    stores.clients.put(client).await
    Post(
      "/token",
      FormData(
        "grant_type" -> "client_credentials",
        "scope" -> scope
      )
    ).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("scope") should be(true)
    }
  }

  they should "handle refresh token grants (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val token = Generators.generateRefreshToken
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/token" +
        s"?grant_type=refresh_token" +
        s"&refresh_token=${token.value}" +
        s"&scope=$scope"

    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.tokens.put(client.id, token, owner, Some(scope)).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val newTokenGenerated = stores.tokens.all.await.headOption.exists(_.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "handle refresh token grants (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val token = Generators.generateRefreshToken
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.tokens.put(client.id, token, owner, Some(scope)).await
    Post(
      "/token",
      FormData(
        "grant_type" -> "refresh_token",
        "refresh_token" -> token.value,
        "scope" -> scope
      )
    ).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val newTokenGenerated = stores.tokens.all.await.headOption.exists(_.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "handle password token grants (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(ownerRawPassword, ownerSalt)(secrets.owner),
      salt = ownerSalt
    )
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/token" +
        s"?grant_type=password" +
        s"&username=${owner.username}" +
        s"&password=$ownerRawPassword" +
        s"&scope=$scope"

    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle password token grants (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(ownerRawPassword, ownerSalt)(secrets.owner),
      salt = ownerSalt
    )
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    Post(
      "/token",
      FormData(
        "grant_type" -> "password",
        "username" -> owner.username,
        "password" -> ownerRawPassword,
        "scope" -> scope
      )
    ).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "reject token grants with invalid grant type" in withRetry {
    val (_, _, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val grantType = "some-grant"

    val uri = s"/token?grant_type=$grantType"

    Post(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        s"The request includes an invalid grant type: [$grantType]"
      )
    }
  }

  they should "reject requests when clients fail authentication" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive("some-password", salt)(secrets.client),
      salt = salt
    )

    val scope = s"${AudienceExtraction.UrnPrefix}:${client.id}"

    val uri =
      s"/token" +
        s"?grant_type=client_credentials" +
        s"&scope=$scope"

    stores.clients.put(client).await
    Post(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  they should "reject requests when owners fail authentication" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val oauth = new OAuth(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive("some-password", salt)(secrets.owner),
      salt = salt
    )

    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"
    val state = layers.Generators.generateString(withSize = 16)

    val uri =
      s"/authorization" +
        s"?response_type=token" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state"

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)
      headers.find(_.is("location")) match {
        case Some(location) =>
          val query = AuthorizationError.AccessDenied(withState = state).asQuery
          location.value().endsWith(query.toString) should be(true)

        case None =>
          fail("Unexpected response received; location header not found")
      }
    }
  }
}
