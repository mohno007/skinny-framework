package skinny.session

import javax.servlet._
import javax.servlet.http._
import skinny.filter.SkinnySessionFilter
import skinny.logging.LoggerProvider
import skinny.session.jdbc.SkinnySession
import skinny.session.servlet._

/**
  * Servlet filter to attach skinny sessions to Servlet session due to invalidation.
  *
  * {{{
  *  class ScalatraBootstrap extends SkinnyLifeCycle {
  *    override def initSkinnyApp(ctx: ServletContext) {
  *      ctx.mount(classOf[SkinnySessionInitializer], "/\*")
  *      ....
  * }}}
  */
class SkinnySessionInitializer extends Filter with LoggerProvider {

  // just default settings, this might be updated by yourself
  def except: Seq[String] = Seq("/assets/")

  /**
    * Provides SkinnyHttpSession (by default JDBC implementation).
    *
    * @param req Http request
    * @return skinny http session
    */
  def getSkinnyHttpSession(req: HttpServletRequest): SkinnyHttpSession = {
    val session              = req.getSession(true)
    val jsessionIdCookieName = req.getServletContext.getSessionCookieConfig.getName
    val jsessionIdInCookie   = Option(req.getCookies).flatMap(_.find(_.getName == jsessionIdCookieName).map(_.getValue))
    val jsessionIdInCookieIdPart =
      jsessionIdInCookie.map(jsessionIdMayHaveWorkerName => jsessionIdMayHaveWorkerName.split('.').head)
    val jsessionIdInSession = session.getId
    logger.debug(
      s"[Skinny Session] session id (cookie: ${jsessionIdInCookie}(id: ${jsessionIdInCookieIdPart}), local session: ${jsessionIdInSession})"
    )
    val expireAt = SkinnySession.getExpireAtFromMaxInactiveInterval(session.getMaxInactiveInterval)
    val skinnySession = if (jsessionIdInCookieIdPart.isDefined && jsessionIdInCookieIdPart.get != jsessionIdInSession) {
      SkinnySession.findOrCreate(jsessionIdInCookieIdPart.get, Option(jsessionIdInSession), expireAt)
    } else {
      SkinnySession.findOrCreate(jsessionIdInSession, None, expireAt)
    }
    new SkinnyHttpSessionJDBCImpl(session, skinnySession)
  }

  override def init(filterConfig: FilterConfig) = {}
  override def destroy()                        = {}

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) = {
    val req = request.asInstanceOf[HttpServletRequest]
    if (except.exists(e => req.getServletPath.startsWith(e))) {
      chain.doFilter(request, response)
    } else {
      val skinnySession = getSkinnyHttpSession(req)
      req.setAttribute(SkinnySessionFilter.ATTR_SKINNY_SESSION_IN_REQUEST_SCOPE, skinnySession)
      chain.doFilter(new SkinnyHttpRequestWrapper(
                       req,
                       SkinnyHttpSessionWrapper(req.getSession, skinnySession)
                     ),
                     response)
    }
  }

}
