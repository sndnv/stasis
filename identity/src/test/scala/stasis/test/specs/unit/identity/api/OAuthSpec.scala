package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes, Uri}
import stasis.identity.api.OAuth
import stasis.identity.api.oauth.directives.AudienceExtraction
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.oauth.OAuthFixtures
import stasis.test.specs.unit.identity.model.Generators

class OAuthSpec extends RouteTest with OAuthFixtures {
  "OAuth routes" should "handle code authorization requests" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val client = Generators.generateClient.copy(realm = realm.id)
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val state = Generators.generateString(withSize = 16)
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/${realm.id}/authorization" +
        s"?response_type=code" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state"

    stores.realms.put(realm).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)
      stores.codes.get(client.id).await match {
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

  they should "handle PKCE code authorization requests" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val client = Generators.generateClient.copy(realm = realm.id)
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val state = Generators.generateString(withSize = 16)
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"
    val storedChallenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = None
    )

    val uri =
      s"/${realm.id}/authorization" +
        s"?response_type=code" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state" +
        s"&code_challenge=${storedChallenge.value}"

    stores.realms.put(realm).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.Found)
      stores.codes.get(client.id).await match {
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

  they should "handle token authorization requests" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val client = Generators.generateClient.copy(realm = realm.id)
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"
    val state = Generators.generateString(withSize = 16)

    val uri =
      s"/${realm.id}/authorization" +
        s"?response_type=token" +
        s"&client_id=${client.id}" +
        s"&scope=$scope" +
        s"&state=$state"

    stores.realms.put(realm).await
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
          headerValue.contains(s"expires_in=${client.tokenExpiration.value}") should be(true)
          headerValue.contains(s"state=$state") should be(true)
          headerValue.contains(s"scope=$scope") should be(true)

        case None =>
          fail(s"Unexpected response received; no location found in headers [$headers]")
      }
    }
  }

  they should "reject authorization requests with invalid response types" in {
    val (stores, _, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val responseType = "some-response"

    val uri =
      s"/${realm.id}/authorization" +
        s"?response_type=$responseType"

    stores.realms.put(realm).await
    Get(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        s"Realm [${realm.id}]: The request includes an invalid response type: [$responseType]"
      )
    }
  }

  they should "handle authorization code token grants" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedCode = StoredAuthorizationCode(
      code,
      owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}")
    )

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=authorization_code" +
        s"&code=${code.value}" +
        s"&client_id=${client.id}"

    stores.realms.put(realm).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle PKCE authorization code token grants" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val storedChallenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = None
    )

    val storedCode = StoredAuthorizationCode(
      code,
      owner,
      scope = Some(s"${AudienceExtraction.UrnPrefix}:${api.id}"),
      Some(storedChallenge)
    )

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=authorization_code" +
        s"&code=${code.value}" +
        s"&client_id=${client.id}" +
        s"&code_verifier=${storedChallenge.value}"

    stores.realms.put(realm).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "handle client credentials token grants" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val scope = s"${AudienceExtraction.UrnPrefix}:${client.id}"

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=client_credentials" +
        s"&scope=$scope"

    stores.realms.put(realm).await
    stores.clients.put(client).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("scope") should be(true)
    }
  }

  they should "handle refresh token grants" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner.copy(realm = realm.id)
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val token = Generators.generateRefreshToken
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=refresh_token" +
        s"&refresh_token=${token.value}" +
        s"&scope=$scope"

    stores.realms.put(realm).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.tokens.put(client.id, token, owner, Some(scope)).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val newTokenGenerated = stores.tokens.get(client.id).await.exists(_.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "handle password token grants" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(ownerRawPassword, ownerSalt)(secrets.owner),
      salt = ownerSalt
    )
    val scope = s"${AudienceExtraction.UrnPrefix}:${api.id}"

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=password" +
        s"&username=${owner.username}" +
        s"&password=$ownerRawPassword" +
        s"&scope=$scope"

    stores.realms.put(realm).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    Post(uri).addCredentials(credentials) ~> oauth.routes ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[String]
      actualResponse.contains("access_token") should be(true)
      actualResponse.contains("token_type") should be(true)
      actualResponse.contains("expires_in") should be(true)
      actualResponse.contains("refresh_token") should be(true)
      actualResponse.contains("scope") should be(true)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "reject token grants with invalid grant type" in {
    val (stores, _, providers) = createOAuthFixtures()
    val oauth = new OAuth(providers)

    val realm = Generators.generateRealm
    val grantType = "some-grant"

    val uri =
      s"/${realm.id}/token" +
        s"?grant_type=$grantType"

    stores.realms.put(realm).await
    Post(uri) ~> oauth.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        s"Realm [${realm.id}]: The request includes an invalid grant type: [$grantType]"
      )
    }
  }
}
