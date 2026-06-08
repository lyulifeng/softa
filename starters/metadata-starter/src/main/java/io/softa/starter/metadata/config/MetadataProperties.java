package io.softa.starter.metadata.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for metadata starter.
 *
 * <p>Bound to {@code system.metadata.*} in {@code application.yml}.
 *
 * @param scannerScope ordered list of regex patterns, each
 *     {@linkplain java.util.regex.Matcher#matches() full-matched} against a
 *     class's {@linkplain Class#getPackageName() package name}, deciding which
 *     {@code @Model} / {@code @OptionSet} declarations the
 *     {@code MetadataAnnotationScanner} reconciles into {@code sys_*} (and
 *     applies DDL for) at boot.
 *     <ul>
 *       <li>A sole entry {@code "*"} ⇒ match every package (manage all —
 *           equivalent to the former {@code dev-mode=true}).</li>
 *       <li>Empty / unset ⇒ the scanner manages nothing and the
 *           {@code MetadataAnnotationChecker} instead observes the whole
 *           catalog read-only and WARNs on drift (the safe default —
 *           equivalent to the former {@code dev-mode=false}).</li>
 *       <li>A partial list ⇒ the scanner reconciles only in-scope packages;
 *           rows outside the scope are never read, written, or deleted (lets
 *           developers share one dev database on disjoint package areas).</li>
 *     </ul>
 *     Dots are regex metacharacters — write {@code io\.softa\.foo.*} to include
 *     sub-packages, {@code io\.softa\.foo} for that exact package only.
 *     <b>Never set a non-empty scope on a production runtime.</b>
 * @param publicKey base64-encoded X.509 Ed25519 public key trusted by this
 *     runtime when verifying signatures on studio-issued requests. Blank /
 *     unset means signature verification is disabled (the filter bean is not
 *     registered).
 */
@ConfigurationProperties("system.metadata")
public record MetadataProperties(List<String> scannerScope, String publicKey) {

    public MetadataProperties {
        scannerScope = scannerScope == null ? List.of() : List.copyOf(scannerScope);
    }
}
