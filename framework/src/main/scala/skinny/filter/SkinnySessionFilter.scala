package skinny.filter

import scala.language.implicitConversions

import skinny.controller.feature._
import skinny.engine.context.SkinnyEngineContext
import skinny.engine.csrf.{ CsrfTokenSupport, CsrfTokenGenerator }
import skinny.engine.flash.{ FlashMapSupport, FlashMap }
import skinny.session._
import FlashMapSupport._

import java.util.Locale
import javax.servlet.http.HttpServletRequest

object SkinnySessionFilter {

  val ATTR_SKINNY_SESSION_IN_REQUEST_SCOPE = classOf[SkinnySessionFilter].getCanonicalName + "_SkinnySessionWrapper"

}
/**
 * Enables replacing Servlet session with Skinny's session shared among several Servlet apps.
 *
 * Mounting skinny.session.SkinnySessionInitializer on the top of ScalatraBootstrap.scala is required.
 *
 * {{{
 *   ctx.mount(classOf[SkinnySessionInitializer], "/\*")
 * }}}
 */
trait SkinnySessionFilter extends SkinnyFilter {

  self: FlashFeature with CSRFProtectionFeature with LocaleFeature =>

  import SkinnySessionFilter._

  // --------------------------------------
  // SkinnySession by using Skinny beforeAction/afterAction

  beforeAction()(initializeSkinnySession)

  afterAction()(saveCurrentSkinnySession)

  protected def initializeSkinnySession: SkinnyHttpSession = SkinnyHttpSession.getOrCreate(request)

  protected def saveCurrentSkinnySession(): Unit = {
    try {
      getFromRequestScope[SkinnyHttpSession](ATTR_SKINNY_SESSION_IN_REQUEST_SCOPE).foreach { sessionWrapper =>
        sessionWrapper.save()
      }
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to save skinny session because ${e.getMessage}", e)
    }
  }

  // --------------------------------------
  // Accessing SkinnySession

  def skinnySession(implicit ctx: SkinnyEngineContext): SkinnyHttpSession = {
    getFromRequestScope[SkinnyHttpSession](ATTR_SKINNY_SESSION_IN_REQUEST_SCOPE)(ctx).getOrElse {
      initializeSkinnySession
    }
  }

  def skinnySession[A](key: String)(implicit ctx: SkinnyEngineContext): Option[A] = skinnySession(ctx).getAs[A](key)

  def skinnySession[A](key: Symbol)(implicit ctx: SkinnyEngineContext): Option[A] = skinnySession[A](key.name)(ctx)

  // --------------------------------------
  // override FlashMapSupport
  // NOTICE: This API doesn't support Future ops

  override def flashMapSetSession(f: FlashMap)(implicit ctx: SkinnyEngineContext): Unit = {
    try {
      skinnySession(ctx).setAttribute(SessionKey, f)
    } catch {
      case e: Throwable => logger.debug(s"Failed to set flashMap to skinny session because ${e.getMessage}")
    }
  }

  // --------------------------------------
  // override CsrfTokenSupport

  override protected def isForged: Boolean = {
    if (skinnySession.getAttribute(csrfKey).isEmpty) {
      prepareCsrfToken()
    }
    !request.requestMethod.isSafe &&
      skinnySession.getAttribute(csrfKey) != params.get(csrfKey) &&
      !CsrfTokenSupport.HeaderNames.map(request.headers.get).contains(skinnySession.getAttribute(csrfKey))
  }

  override protected def prepareCsrfToken() = {
    skinnySession.getAttributeOrElseUpdate(csrfKey, CsrfTokenGenerator())
  }

  // --------------------------------------
  // override SessionLocaleFeature

  override def setCurrentLocale(locale: String)(implicit ctx: SkinnyEngineContext): Unit = {
    skinnySession(ctx).setAttribute(sessionLocaleKey, locale)
  }

  override def currentLocale(implicit ctx: SkinnyEngineContext): Option[Locale] = {
    skinnySession(ctx)
      .getAttribute(sessionLocaleKey)
      .map(l => new Locale(l.toString))
      .orElse(defaultLocale)
  }

}
