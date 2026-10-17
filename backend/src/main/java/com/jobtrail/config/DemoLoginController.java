package com.jobtrail.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Controller
public class DemoLoginController {
    private final boolean authenticationEnabled;
    private final boolean demoAvailable;

    public DemoLoginController(@Value("${jobtrail.security.enabled:false}") boolean authenticationEnabled,
            @Value("${jobtrail.demo.enabled:true}") boolean demoEnabled) {
        this.authenticationEnabled = authenticationEnabled;
        this.demoAvailable = authenticationEnabled && demoEnabled;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(HttpServletRequest request) {
        if (!authenticationEnabled) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String hidden = "<input type=\"hidden\" name=\"" + HtmlUtils.htmlEscape(csrf.getParameterName())
                + "\" value=\"" + HtmlUtils.htmlEscape(csrf.getToken()) + "\">";
        String demo = demoAvailable ? """
                <section class="demo-panel" aria-labelledby="demo-heading">
                  <div class="eyebrow"><span class="eyebrow-line" aria-hidden="true"></span> EXPLORE JOBTRAIL</div>
                  <h1 id="demo-heading">A search with<br><span>direction.</span></h1>
                  <p class="hero-intro">A little more clarity for your next move. Explore real opportunities in a temporary workspace.</p>
                  <ul class="capabilities">
                    <li><span class="capability-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></svg></span><div><strong>Go straight to the source</strong><p>Search listings from official employer boards.</p></div></li>
                    <li><span class="capability-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M8 4H5a1 1 0 0 0-1 1v15h16V5a1 1 0 0 0-1-1h-3M8 3h8v4H8zM8 12h8M8 16h5"/></svg></span><div><strong>Find the connection</strong><p>Compare roles with an editable, fictional profile.</p></div></li>
                    <li><span class="capability-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6 3h12v18l-6-4-6 4z"/></svg></span><div><strong>Keep opportunities in view</strong><p>Save roles and try the application tracker.</p></div></li>
                  </ul>
                  <form class="demo-form" action="/demo" method="post">{{csrf}}
                    <span class="no-account">No account or password needed</span>
                    <button class="demo-button" type="submit" aria-describedby="demo-reset">Try temporary demo <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 12h14m-6-6 6 6-6 6"/></svg></button>
                  </form>
                  <p id="demo-reset" class="reset-notice"><strong>This demo is not permanent.</strong> Changes reset when you sign out, after 30 minutes of inactivity, or when the service restarts.</p>
                </section>
                """.replace("{{csrf}}", hidden) : "";
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="robots" content="noindex"><title>Sign in | JobTrail</title>
                <style>
                *{box-sizing:border-box}body{margin:0;background:#f7f8f4;color:#20342e;font-family:"Segoe UI Variable","Segoe UI",-apple-system,BlinkMacSystemFont,sans-serif;line-height:1.55;-webkit-font-smoothing:antialiased}button,input{font:inherit}button{touch-action:manipulation}p,h1,h2{margin:0}svg{display:block;flex-shrink:0}
                main{max-width:1120px;margin:0 auto;padding:38px 28px 26px}.page-header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:32px}.brand{display:flex;align-items:center;gap:11px;font-size:27px;font-weight:750;letter-spacing:-1px}.brand-word{display:flex}.brand-dot{color:#0c6d62}.brand-mark{display:grid;place-items:center;width:40px;height:40px;border-radius:12px;color:#fff;background:#0c6d62}.brand-mark svg{width:25px;height:25px}.project-status{display:flex;align-items:center;gap:8px;color:#52675d;font-size:12px;font-weight:600}.status-dot{width:6px;height:6px;border-radius:50%;background:#0c6d62}
                .panels{display:grid;grid-template-columns:minmax(0,1.16fr) minmax(0,.84fr);gap:28px;align-items:center}.panels.owner-only{grid-template-columns:minmax(0,480px);justify-content:center;padding:24px 0}.demo-panel{padding:40px 42px 34px;border-radius:24px;background:#123f38;color:#fff}.eyebrow{display:flex;align-items:center;gap:9px;font-size:11px;font-weight:700;letter-spacing:1.6px}.demo-panel .eyebrow{color:#b9d7c8}.eyebrow-line{width:22px;height:1px;background:#91bca7}.demo-panel h1{margin:21px 0 17px;font-size:clamp(38px,4.5vw,54px);line-height:1.06;font-weight:650;letter-spacing:-2.4px}.demo-panel h1 span{color:#bddbc1}.hero-intro{max-width:370px;color:#cfdfd8;font-size:15px;line-height:1.7}
                .capabilities{display:grid;gap:19px;list-style:none;margin:29px 0 30px;padding:0}.capabilities li{display:flex;align-items:center;gap:13px}.capability-icon{display:grid;place-items:center;flex-shrink:0;width:38px;height:38px;border:1px solid #3a6255;border-radius:11px;color:#cae2cd}.capability-icon svg{width:20px;height:20px}.capabilities strong{display:block;font-size:14px;font-weight:650;color:#f3f8f2}.capabilities p{font-size:13px;color:#c4d8cf;margin-top:3px}.no-account{display:block;color:#c8ddd1;font-size:12px;margin-bottom:10px}.demo-form button{margin:0}.demo-button{background:#d0e6bf;color:#183d30}.demo-button:hover{background:#e0efcf}.demo-button svg{width:20px;height:20px}.reset-notice{font-size:12px;line-height:1.65;color:#bfd3c9;margin-top:15px}.reset-notice strong{font-weight:600;color:#e0ebe2}
                .owner-panel{padding:37px 34px;border:1px solid #e2e7df;border-radius:20px;background:#fff;box-shadow:0 5px 24px #20342e04}.owner-panel .eyebrow{color:#65796e}.owner-panel h1,.owner-panel h2{font-size:28px;font-weight:650;line-height:1.25;letter-spacing:-.8px;margin:13px 0 10px}.owner-intro{font-size:14px;color:#617168;line-height:1.7;margin-bottom:25px}.owner-panel label{display:block;font-weight:600;font-size:13px;margin:19px 0 8px}.owner-panel input{width:100%;min-height:47px;padding:12px 13px;border:1px solid #d7dfd5;border-radius:9px;background:#fcfdfb;color:#20342e;font-size:15px}.owner-panel input:hover{border-color:#aebfb2}.owner-panel input:focus{border-color:#0c6d62;background:#fff}.owner-panel input[aria-invalid=true]{border-color:#af5947}
                button{display:flex;align-items:center;justify-content:center;gap:12px;width:100%;min-height:48px;padding:13px 16px;border:0;border-radius:9px;background:#0c6d62;color:#fff;font-size:14px;font-weight:650;cursor:pointer;margin-top:26px}button:hover{background:#08564d}button:focus-visible,input:focus-visible{outline:3px solid #62b4a6;outline-offset:3px}.demo-button:focus-visible{outline-color:#e4f2cf}.privacy-note{display:flex;align-items:flex-start;gap:9px;margin-top:24px;padding-top:22px;border-top:1px solid #edf0e9;color:#65756b;font-size:13px;line-height:1.65}.privacy-note svg{width:16px;height:16px;margin-top:3px;color:#6c8576}.error{background:#fff1ed;border:1px solid #e5b4a4;padding:12px;border-radius:9px;color:#8b3723;font-size:13px;line-height:1.6;margin:16px 0}
                footer{display:flex;justify-content:space-between;gap:20px;margin-top:27px;color:#637268;font-size:12px;line-height:1.7}footer p:first-child{flex-shrink:0}footer p:last-child{max-width:510px;text-align:right}.footer-tech{font-weight:600;color:#3d5e4e}::selection{background:#c4e7d7;color:#18372f}
                @media(max-width:900px){main{padding:28px 22px}.panels{gap:20px}.demo-panel{padding:32px 28px}.owner-panel{padding:30px 26px}.demo-panel h1{font-size:44px}}
                @media(max-width:720px){main{max-width:560px;padding:25px 18px}.page-header{margin-bottom:25px}.brand{font-size:25px}.project-status{font-size:11px}.panels{grid-template-columns:minmax(0,1fr);gap:22px}.demo-panel{padding:32px 28px}.demo-panel h1{font-size:48px}.owner-panel{padding:30px 28px}.panels.owner-only{padding:0}footer{display:block;margin-top:24px}footer p:last-child{text-align:left;margin-top:8px}.owner-panel input{font-size:16px}}
                @media(max-width:360px){main{padding:20px 14px}.project-status{display:none}.demo-panel,.owner-panel{padding:28px 22px}.demo-panel h1{font-size:42px}}
                </style></head><body><main>
                <header class="page-header"><div class="brand"><span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="17" r="3"/><path d="M9 17h7a4 4 0 0 0 0-8H8a3 3 0 0 1 0-6h7M15 3l-2-2m2 2-2 2"/></svg></span><span class="brand-word">JobTrail<span class="brand-dot">.</span></span></div><div class="project-status"><span class="status-dot" aria-hidden="true"></span> A project in progress</div></header>
                <div class="panels {{owner-only}}">{{demo}}<section class="owner-panel" aria-labelledby="owner-heading"><div class="eyebrow">PERSONAL WORKSPACE</div>{{owner-title}}
                <p class="owner-intro">Welcome back. Sign in to your private profile, saved roles, and application history.</p>
                {{error}}<form action="/login" method="post">{{csrf}}<label for="username">Username</label><input id="username" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" {{invalid}} required>
                <label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" {{invalid}} required>
                <button type="submit">Sign in</button></form><p class="privacy-note"><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/></svg><span>Owner access keeps your personal profile and application history private.</span></p></section></div>
                <footer><p>Built with <span class="footer-tech">Java &amp; Spring Boot</span> · Ongoing project</p><p>Matching currently uses rules. AI job search and other AI features are planned. JobTrail does not submit applications.</p></footer></main></body></html>
                """.replace("{{csrf}}", hidden)
                .replace("{{error}}", request.getParameter("error") == null ? ""
                        : "<p id=\"login-error\" class=\"error\" role=\"alert\">The username or password was incorrect. Please try again.</p>")
                .replace("{{invalid}}", request.getParameter("error") == null ? ""
                        : "aria-invalid=\"true\" aria-describedby=\"login-error\"")
                .replace("{{owner-only}}", demoAvailable ? "" : "owner-only")
                .replace("{{owner-title}}", demoAvailable ? "<h2 id=\"owner-heading\">Owner sign in</h2>"
                        : "<h1 id=\"owner-heading\">Owner sign in</h1>")
                .replace("{{demo}}", demo);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/demo")
    public String demoLanding() { return "redirect:/login"; }

    @PostMapping("/demo")
    public String start(HttpServletRequest request, HttpServletResponse response) {
        if (!demoAvailable) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        HttpSession previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(30 * 60);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "demo-" + UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        CookieCsrfTokenRepository.withHttpOnlyFalse().saveToken(null, request, response);
        return "redirect:/";
    }
}
