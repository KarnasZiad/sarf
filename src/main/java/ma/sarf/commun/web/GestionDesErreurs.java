package ma.sarf.commun.web;

import java.net.URI;
import ma.sarf.commun.domaine.ConflitDeVersionException;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.commun.domaine.RegleMetierException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Erreurs au format « Problem Details » (RFC 9457). Une règle métier violée renvoie le code de
 * la règle et l'article du texte qui la fonde.
 */
@RestControllerAdvice
class GestionDesErreurs {

    @ExceptionHandler(RegleMetierException.class)
    ProblemDetail regleViolee(RegleMetierException exception) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        probleme.setTitle("Règle réglementaire non respectée");
        probleme.setType(URI.create("urn:sarf:regle:" + exception.code()));
        probleme.setProperty("code", exception.code());
        probleme.setProperty("reference", exception.reference());
        return probleme;
    }

    @ExceptionHandler(ObjetIntrouvableException.class)
    ProblemDetail introuvable(ObjetIntrouvableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /** Donnée refusée par une règle de validité du domaine : le message s'adresse à l'utilisateur. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail donneeInvalide(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail fichierTropVolumineux(MaxUploadSizeExceededException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Le fichier dépasse la taille maximale de 10 Mo.");
    }

    @ExceptionHandler(ConflitDeVersionException.class)
    ProblemDetail conflit(ConflitDeVersionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
