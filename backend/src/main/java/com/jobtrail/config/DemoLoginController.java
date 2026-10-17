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
                  <div class="eyebrow">EXPLORE THE PROJECT</div>
                  <h2 id="demo-heading">Try a temporary demo</h2>
                  <p>Browse real job listings with a fictional profile. Try matching, saving roles, and editing sample experience.</p>
                  <p class="detail">This demo is not permanent. Changes stay in this session and reset when you sign out, after 30 minutes of inactivity, or when the service restarts. No account or password needed.</p>
                  <form action="/demo" method="post">{{csrf}}<button type="submit">Try temporary demo <span aria-hidden="true">&#8599;</span></button></form>
                  <p class="fine-print">Matching currently uses rules. AI job search and other AI features are planned. JobTrail does not submit applications.</p>
                </section>
                """.replace("{{csrf}}", hidden) : "";
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="robots" content="noindex"><title>Sign in | JobTrail</title>
                <style>
                *{box-sizing:border-box}body{margin:0;background:#f3f6f5;color:#18372f;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.55}
                main{max-width:960px;margin:0 auto;padding:54px 24px}.brand{font-size:28px;font-weight:750;letter-spacing:-1px;margin-bottom:28px}.brand span{color:#158267}
                h1{font-size:clamp(30px,5vw,44px);line-height:1.15;letter-spacing:-1.5px;margin:0 0 12px}.intro{color:#64776f;margin-bottom:32px}.panels{display:grid;grid-template-columns:1fr 1fr;gap:24px;align-items:start}
                section{padding:30px;border:1px solid #dce5df;border-radius:18px;background:white}.demo-panel{background:#e6f1eb;border-color:#c8dfd0}.eyebrow{font-size:11px;font-weight:750;letter-spacing:1.7px;color:#597168}h2{font-size:23px;letter-spacing:-.5px;margin:10px 0 16px}
                p{font-size:14px;color:#52675d}.detail{font-size:13px}.fine-print{font-size:12px;color:#60766a;margin-bottom:0}label{display:block;font-weight:650;font-size:13px;margin:17px 0 7px}input{width:100%;padding:12px 13px;border:1px solid #cbd8d0;border-radius:8px;font:inherit;background:#fff;color:#18372f}input:focus{outline:2px solid #16866a;outline-offset:2px}
                button{display:flex;align-items:center;justify-content:center;gap:12px;width:100%;padding:13px 16px;border:0;border-radius:9px;background:#147a5d;color:white;font:inherit;font-size:14px;font-weight:650;cursor:pointer;margin-top:22px}button:hover{background:#0f6149}button:focus-visible{outline:3px solid #16866a;outline-offset:3px}.error{background:#fff1ed;border:1px solid #e5b4a4;padding:10px 12px;border-radius:8px;color:#8b3723;font-size:13px}
                footer{font-size:12px;color:#6c7d74;margin-top:24px}@media(max-width:650px){main{padding:30px 18px}.panels{grid-template-columns:1fr}section{padding:24px}.demo-panel{grid-row:1}}
                </style></head><body><main><div class="brand">JobTrail<span>.</span></div>
                <h1>A search with direction.</h1><p class="intro">Official employer listings, evidence-based matching, and a place to track your next move.</p>
                <div class="panels"><section aria-labelledby="owner-heading"><div class="eyebrow">PERSONAL WORKSPACE</div><h2 id="owner-heading">Owner sign in</h2>
                {{error}}<form action="/login" method="post">{{csrf}}<label for="username">Username</label><input id="username" name="username" autocomplete="username" required>
                <label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" required>
                <button type="submit">Sign in</button></form><p class="fine-print">Owner access keeps personal profile and application history private.</p></section>{{demo}}</div>
                <footer>JobTrail is an ongoing Java and Spring Boot project.</footer></main></body></html>
                """.replace("{{csrf}}", hidden)
                .replace("{{error}}", request.getParameter("error") == null ? ""
                        : "<p class=\"error\" role=\"alert\">The username or password was incorrect. Please try again.</p>")
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
