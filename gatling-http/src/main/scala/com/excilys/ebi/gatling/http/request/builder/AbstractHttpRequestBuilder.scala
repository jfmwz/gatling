/**
 * Copyright 2011 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.excilys.ebi.gatling.http.request.builder

import com.ning.http.client.Request
import com.ning.http.client.RequestBuilder
import com.ning.http.client.FluentStringsMap
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import org.fusesource.scalate._
import com.excilys.ebi.gatling.core.context.Context
import com.excilys.ebi.gatling.core.context.SavedValue
import com.excilys.ebi.gatling.core.log.Logging
import com.excilys.ebi.gatling.http.config.HttpProtocolConfiguration
import com.excilys.ebi.gatling.http.config.HttpProtocolConfiguration._
import com.excilys.ebi.gatling.http.request.Param
import com.excilys.ebi.gatling.http.request.StringParam
import com.excilys.ebi.gatling.http.request.ContextParam
import com.excilys.ebi.gatling.http.request.HttpRequestBody
import com.excilys.ebi.gatling.http.request.FilePathBody
import com.excilys.ebi.gatling.http.request.StringBody
import com.excilys.ebi.gatling.http.request.TemplateBody
import com.excilys.ebi.gatling.http.request.MIMEType._
import com.excilys.ebi.gatling.http.util.HttpHelper._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import com.excilys.ebi.gatling.http.action.HttpRequestActionBuilder
import com.excilys.ebi.gatling.http.request.HttpRequest
import com.ning.http.client.Cookie
import scala.collection.immutable.HashMap
import com.ning.http.client.Realm
import com.ning.http.client.Realm.AuthScheme
import com.excilys.ebi.gatling.http.check.HttpCheckBuilder
import com.excilys.ebi.gatling.core.util.StringHelper._

/**
 * AbstractHttpRequestBuilder class companion
 */
object AbstractHttpRequestBuilder {
	/**
	 * Implicit converter from requestBuilder to HttpRequestActionBuilder
	 *
	 * @param requestBuilder the request builder to convert
	 */
	implicit def toHttpRequestActionBuilder[B <: AbstractHttpRequestBuilder[B]](requestBuilder: B) = requestBuilder.httpRequestActionBuilder withRequest (new HttpRequest(requestBuilder.httpRequestActionBuilder.requestName, requestBuilder))
}

/**
 * This class serves as model for all HttpRequestBuilders
 *
 * @param httpRequestActionBuilder the HttpRequestActionBuilder with which this builder is linked
 * @param urlFunction the function returning the url
 * @param queryParams the query parameters that should be added to the request
 * @param headers the headers that should be added to the request
 * @param followsRedirects sets the follow redirect option of AHC
 * @param credentials sets the credentials in case of Basic HTTP Authentication
 */
abstract class AbstractHttpRequestBuilder[B <: AbstractHttpRequestBuilder[B]](val httpRequestActionBuilder: HttpRequestActionBuilder, urlFunction: Option[Context => String],
	queryParams: List[(Context => String, Context => Option[String])], headers: Map[String, String], followsRedirects: Option[Boolean], credentials: Option[(String, String)])
		extends Logging {

	/**
	 * Method overridden in children to create a new instance of the correct type
	 *
	 * @param httpRequestActionBuilder the HttpRequestActionBuilder with which this builder is linked
	 * @param urlFunction the function returning the url
	 * @param queryParams the query parameters that should be added to the request
	 * @param headers the headers that should be added to the request
	 * @param followsRedirects sets the follow redirect option of AHC
	 * @param credentials sets the credentials in case of Basic HTTP Authentication
	 */
	def newInstance(httpRequestActionBuilder: HttpRequestActionBuilder, urlFunction: Option[Context => String], queryParams: List[(Context => String, Context => Option[String])], headers: Map[String, String], followsRedirects: Option[Boolean], credentials: Option[(String, String)]): B

	/**
	 * Stops defining the request and adds checks on the response
	 *
	 * @param checkBuilders the checks that will be performed on the reponse
	 */
	def check(checkBuilders: HttpCheckBuilder[_]*) = httpRequestActionBuilder withRequest (new HttpRequest(httpRequestActionBuilder.requestName, this)) withProcessors checkBuilders

	/**
	 * Adds a query parameter to the request
	 *
	 * @param paramKeyFunction a function that returns the key name
	 * @param paramValueFunction a function that returns the value
	 */
	def queryParam(paramKeyFunction: Context => String, paramValueFunction: Context => Option[String]): B = newInstance(httpRequestActionBuilder, urlFunction, (paramKeyFunction, paramValueFunction) :: queryParams, headers, followsRedirects, credentials)
	/**
	 * Adds a query parameter to the request
	 *
	 * Its key and value are set by the user
	 *
	 * @param paramKey the key of the parameter
	 * @param paramValue the value of the parameter
	 */
	def queryParam(paramKey: String, paramValue: String): B = queryParam((c: Context) => paramKey, (c: Context) => Some(paramValue))

	/**
	 * Adds a query parameter to the request
	 *
	 * Its key and value come from the context
	 *
	 * @param paramKey the key of the context containing the name of the key
	 * @param paramValue the key of the context containing the value of the parameter
	 */
	def queryParam(paramKey: SavedValue, paramValue: SavedValue): B = queryParam((c: Context) => c.getAttribute(paramKey.attributeKey).toString, (c: Context) => Some(c.getAttribute(paramValue.attributeKey).toString))
	/**
	 * Adds a query parameter to the request
	 *
	 * Its key is set by the user and its value comes from the context
	 *
	 * @param paramValue the key of the context containing the value of the parameter
	 */
	def queryParam(paramKey: String, paramValue: SavedValue): B = queryParam((c: Context) => paramKey, (c: Context) => Some(c.getAttribute(paramValue.attributeKey).toString))
	/**
	 * Adds a query parameter to the request
	 *
	 * Its key comes from the context and its value is set by the user
	 *
	 * @param paramKey the key of the context containing the name of the key
	 */
	def queryParam(paramKey: SavedValue, paramValue: String): B = queryParam((c: Context) => c.getAttribute(paramKey.attributeKey).toString, (c: Context) => Some(paramValue))
	/**
	 * Adds a query parameter to the request
	 *
	 * It will only have a key set by the user
	 *
	 * @param paramKey the key of the parameter
	 */
	def queryParam(paramKey: String): B = queryParam((c: Context) => paramKey, (c: Context) => None)
	/**
	 * Adds a query parameter to the request
	 *
	 * It will only have a key coming from context
	 *
	 * @param paramKey the key of the context containing the name of the key
	 */
	def queryParam(paramKey: SavedValue): B = queryParam((c: Context) => c.getAttribute(paramKey.attributeKey).toString, (c: Context) => None)

	/**
	 * Adds a header to the request
	 *
	 * @param header the header to add, eg: ("Content-Type", "application/json")
	 */
	def header(header: (String, String)): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers + (header._1 -> header._2), followsRedirects, credentials)

	/**
	 * Adds several headers to the request at the same time
	 *
	 * @param givenHeaders a scala map containing the headers to add
	 */
	def headers(givenHeaders: Map[String, String]): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers ++ givenHeaders, followsRedirects, credentials)

	/**
	 * Sets the follow redirect option that will be applied on AHC
	 *
	 * @param followRedirect a boolean that activates (true) or deactivates (false) the follow redirect option
	 */
	def followsRedirect(followRedirect: Boolean): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers, Some(followRedirect), credentials)

	/**
	 * Adds Accept and Content-Type headers to the request set with "application/json" values
	 */
	def asJSON(): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers + (ACCEPT -> APPLICATION_JSON) + (CONTENT_TYPE -> APPLICATION_JSON), followsRedirects, credentials)

	/**
	 * Adds Accept and Content-Type headers to the request set with "application/xml" values
	 */
	def asXML(): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers + (ACCEPT -> APPLICATION_XML) + (CONTENT_TYPE -> APPLICATION_XML), followsRedirects, credentials)

	/**
	 * Adds BASIC authentication to the request
	 *
	 * @param username the username needed
	 * @param password the password needed
	 */
	def basicAuth(username: String, password: String): B = newInstance(httpRequestActionBuilder, urlFunction, queryParams, headers, followsRedirects, Some((username, password)))

	def getMethod: String

	/**
	 * This method actually fills the request builder to avoid race conditions
	 *
	 * @param context the context of the current scenario
	 */
	def getRequestBuilder(context: Context): RequestBuilder = {
		logger.debug("Building in HttpRequestBuilder")
		val requestBuilder = new RequestBuilder
		requestBuilder setMethod getMethod setFollowRedirects followsRedirects.getOrElse(false)

		addURLTo(requestBuilder, context)
		addProxyTo(requestBuilder, context)
		addCookiesTo(requestBuilder, context)
		addQueryParamsTo(requestBuilder, context)
		addHeadersTo(requestBuilder, headers)
		addAuthenticationTo(requestBuilder, credentials)

		requestBuilder
	}

	/**
	 * This method builds the request that will be sent
	 *
	 * @param context the context of the current scenario
	 */
	def build(context: Context): Request = {

		val request = getRequestBuilder(context) build

		logger.debug("Built {} Request: {})", getMethod, request.getCookies)
		request
	}

	/**
	 * This method adds proxy information to the request builder if needed
	 *
	 * @param requestBuilder the request builder to which the proxy should be added
	 * @param context the context of the current scenario
	 */
	private def addProxyTo(requestBuilder: RequestBuilder, context: Context) = {
		val httpConfiguration = context.getProtocolConfiguration(HTTP_PROTOCOL_TYPE).map { configuration =>
			configuration.asInstanceOf[HttpProtocolConfiguration]
		}

		if (httpConfiguration.isDefined)
			httpConfiguration.get.proxy.map { proxy =>
				requestBuilder.setProxyServer(proxy)
				logger.debug("PROXY SET")
			}
	}

	/**
	 * This method adds the url to the request builder. It does so by applying the urlFunction to the current context
	 *
	 * @param requestBuilder the request builder to which the url should be added
	 * @param context the context of the current scenario
	 */
	private def addURLTo(requestBuilder: RequestBuilder, context: Context) = {
		val urlProvided = urlFunction.get(context)

		val httpConfiguration = context.getProtocolConfiguration(HTTP_PROTOCOL_TYPE).map { configuration =>
			configuration.asInstanceOf[HttpProtocolConfiguration]
		}

		// baseUrl implementation
		val url =
			if (urlProvided.startsWith("http"))
				urlProvided
			else if (httpConfiguration.isDefined)
				httpConfiguration.get.baseURL.map {
					baseUrl => baseUrl + urlProvided
				}.getOrElse(urlProvided)
			else
				throw new IllegalArgumentException("URL is invalid (does not start with http): " + urlProvided)

		requestBuilder.setUrl(url)
	}

	/**
	 * This method adds the cookies of last response to the request builder
	 *
	 * @param requestBuilder the request builder to which the cookies should be added
	 * @param context the context of the current scenario
	 */
	private def addCookiesTo(requestBuilder: RequestBuilder, context: Context) = {
		logger.debug("Adding Cookies to RequestBuilder: {}", context.getAttributeAsOption(COOKIES_CONTEXT_KEY).getOrElse(Map.empty))
		for ((cookieName, cookie) <- context.getAttributeAsOption(COOKIES_CONTEXT_KEY).getOrElse(HashMap.empty).asInstanceOf[HashMap[String, Cookie]]) {
			logger.debug("Cookie added to request: {}", cookie)
			requestBuilder.addOrReplaceCookie(cookie)
		}
	}

	/**
	 * This method adds the query parameters to the request builder
	 *
	 * @param requestBuilder the request builder to which the query parameters should be added
	 * @param context the context of the current scenario
	 */
	private def addQueryParamsTo(requestBuilder: RequestBuilder, context: Context) = {
		for (queryParam <- queryParams) {
			val (keyFunction, valueFunction) = queryParam
			val (key, value) = (keyFunction.apply(context), valueFunction.apply(context).getOrElse(EMPTY))
			logger.debug("Key: '{}', Value: '{}'", key, value)
			requestBuilder addQueryParameter (key, value)
		}
	}

	/**
	 * This method adds the headers to the request builder
	 *
	 * @param requestBuilder the request builder to which the headers should be added
	 * @param context the context of the current scenario
	 */
	private def addHeadersTo(requestBuilder: RequestBuilder, headers: Map[String, String]) = {
		requestBuilder setHeaders (new FluentCaseInsensitiveStringsMap)
		for (header <- headers) { requestBuilder addHeader (header._1, header._2) }
	}

	/**
	 * This method adds authentication to the request builder if needed
	 *
	 * @param requestBuilder the request builder to which the credentials should be added
	 * @param credentials the credentials to put in the request builder
	 */
	private def addAuthenticationTo(requestBuilder: RequestBuilder, credentials: Option[(String, String)]) = {
		credentials.map { c =>
			val (username, password) = c
			val realm = new Realm.RealmBuilder().setPrincipal(username).setPassword(password).setUsePreemptiveAuth(true).setScheme(AuthScheme.BASIC).build
			requestBuilder.setRealm(realm)
		}
	}
}