<#
.SYNOPSIS
    Joue le scénario de démonstration de Sarf contre la pile Docker Compose (profil « demo »).

.DESCRIPTION
    Chaque étape affiche ce que fait l'acteur, ce que répond l'API et, en cas de refus, la règle et
    l'article qui le fondent. Le script déplace l'horloge de démonstration pour faire expirer les
    délais. Il peut être relancé : les numéros de facture sont uniques à chaque exécution.

    Prérequis : `docker compose up -d` terminé (services « healthy ») et le fichier .env renseigné.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\demo.ps1
#>
param(
    [string] $Api = 'http://localhost:8080',
    [string] $Keycloak = 'http://localhost:8180',
    [string] $MotDePasse = $env:SARF_DEMO_PASSWORD
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient

if (-not $MotDePasse) {
    $env = Join-Path $PSScriptRoot '..\.env'
    $ligne = Get-Content $env -ErrorAction SilentlyContinue | Where-Object { $_ -like 'SARF_DEMO_PASSWORD=*' }
    if (-not $ligne) { throw "SARF_DEMO_PASSWORD introuvable : renseignez-le dans .env ou passez -MotDePasse." }
    $MotDePasse = $ligne.Substring('SARF_DEMO_PASSWORD='.Length)
}

function Titre([string] $texte) {
    Write-Host ''
    Write-Host "== $texte" -ForegroundColor Cyan
}

function Info([string] $texte) { Write-Host "   $texte" }

function Jeton([string] $utilisateur) {
    $formulaire = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    $formulaire['grant_type'] = 'password'
    $formulaire['client_id'] = 'sarf-dev-cli'
    $formulaire['username'] = $utilisateur
    $formulaire['password'] = $MotDePasse
    $contenu = New-Object System.Net.Http.FormUrlEncodedContent -ArgumentList (, $formulaire)
    $reponse = $client.PostAsync("$Keycloak/realms/sarf/protocol/openid-connect/token", $contenu).Result
    $texte = [Text.Encoding]::UTF8.GetString($reponse.Content.ReadAsByteArrayAsync().Result)
    if (-not $reponse.IsSuccessStatusCode) { throw "Connexion de $utilisateur refusée : $texte" }
    return ($texte | ConvertFrom-Json).access_token
}

<# Appelle l'API et renvoie l'objet JSON ; en cas de refus, renvoie le « problem detail » avec Refus = $true. #>
function Appel([string] $methode, [string] $chemin, [string] $jeton, $corps = $null) {
    $requete = New-Object System.Net.Http.HttpRequestMessage -ArgumentList ([System.Net.Http.HttpMethod]::new($methode)), "$Api$chemin"
    $requete.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue -ArgumentList 'Bearer', $jeton
    if ($null -ne $corps) {
        $json = $corps | ConvertTo-Json -Depth 10 -Compress
        $requete.Content = New-Object System.Net.Http.StringContent -ArgumentList $json, ([Text.Encoding]::UTF8), 'application/json'
    }
    $reponse = $client.SendAsync($requete).Result
    $texte = [Text.Encoding]::UTF8.GetString($reponse.Content.ReadAsByteArrayAsync().Result)
    $objet = if ($texte) { $texte | ConvertFrom-Json } else { $null }
    if (-not $reponse.IsSuccessStatusCode) {
        if ($null -eq $objet) { $objet = [pscustomobject]@{} }
        $objet | Add-Member -NotePropertyName Refus -NotePropertyValue $true -Force
        $objet | Add-Member -NotePropertyName Statut -NotePropertyValue ([int] $reponse.StatusCode) -Force
    }
    return $objet
}

function Refus($reponse, [string] $codeAttendu) {
    if (-not $reponse.Refus) { throw "Un refus $codeAttendu était attendu, l'API a accepté." }
    if (-not $reponse.code) {
        Write-Host "   REFUSÉ ($($reponse.Statut)) : ce rôle n'est pas autorisé à faire cette action" -ForegroundColor Yellow
        return
    }
    Write-Host "   REFUSÉ ($($reponse.Statut)) : $($reponse.code)" -ForegroundColor Yellow
    Write-Host "   $($reponse.detail)" -ForegroundColor Yellow
    Write-Host "   Fondement : $($reponse.reference)" -ForegroundColor Yellow
    if ($codeAttendu -and $reponse.code -ne $codeAttendu) { throw "Code attendu : $codeAttendu" }
}

function Ok($reponse) {
    # Une liste n'est jamais un refus : .Refus sur un tableau renverrait un tableau de valeurs vides, considéré comme vrai.
    if ($reponse -isnot [array] -and $reponse.Refus) { throw "Refus inattendu ($($reponse.Statut)) : $($reponse.code) $($reponse.detail) $($reponse.title)" }
    return $reponse
}

function Horloge([string] $date) {
    $null = Ok (Appel 'POST' '/api/demo/horloge' $admin @{ date = $date })
    Write-Host "   [horloge de démonstration : $date]" -ForegroundColor DarkGray
}

$suffixe = Get-Date -Format 'HHmmss'
$rib = '011780000012345678901234'

Titre 'Connexion des cinq acteurs (Keycloak)'
$admin = Jeton 'admin.sarf'
$ordonnateur = Jeton 'ordonnateur.amal'
$comptable = Jeton 'comptable.karim'
$fournisseur = Jeton 'fournisseur.atlas'
$auditeur = Jeton 'auditeur.salma'
Info 'admin.sarf, ordonnateur.amal, comptable.karim, fournisseur.atlas, auditeur.salma : connectés'

Horloge '2026-03-02'
$ligne = (Ok (Appel 'GET' '/api/lignes-budgetaires?exercice=2026' $ordonnateur)) | Where-Object { $_.imputation -eq '1.2.1.10.31' }
Info "Ligne $($ligne.imputation) « $($ligne.libelle) » : $($ligne.disponibleAEngager) DH disponibles"

Titre "1. L'ordonnateur propose un engagement de 100 000 DH (bon de commande)"
$engagement = Ok (Appel 'POST' '/api/engagements' $ordonnateur @{
    ligneBudgetaireId = $ligne.id; serviceOrdonnateur = 'SRV-DSI'
    proposition = @{
        objet = 'Vingt ordinateurs portables'; typeCommande = 'BON_DE_COMMANDE'; referencePiece = "BC-2026-$suffixe"
        fournisseurNom = 'Atlas Informatique SARL'; fournisseurIdentifiant = '001234567000089'
        lignes = @(@{ designation = 'Ordinateur portable'; quantite = 20; prixUnitaire = 5000 })
        montantPropose = 100000
    }
})
Info "Engagement n° $($engagement.id) : $($engagement.montantPropose) DH + somme à valoir $($engagement.sommeAValoir) DH (1 %, décret 2-16-344, art. 12)"
Info "Le comptable doit répondre avant le $($engagement.echeanceVisa) (5 jours ouvrables, décret 2-07-1235, art. 6)"

Titre "2. Une proposition sans pièce justificative : le visa est impossible, le comptable suspend"
$sansPiece = Ok (Appel 'POST' '/api/engagements' $ordonnateur @{
    ligneBudgetaireId = $ligne.id; serviceOrdonnateur = 'SRV-DSI'
    proposition = @{
        objet = 'Imprimantes réseau'; typeCommande = 'BON_DE_COMMANDE'
        fournisseurNom = 'Atlas Informatique SARL'; fournisseurIdentifiant = '001234567000089'
        lignes = @(@{ designation = 'Imprimante'; quantite = 5; prixUnitaire = 9000 })
        montantPropose = 45000
    }
})
Info "Anomalie détectée : $($sansPiece.anomalies[0].message)"
Refus (Appel 'POST' "/api/engagements/$($sansPiece.id)/decision" $comptable @{ type = 'VISA' }) 'VISA_IMPOSSIBLE'
$suspendu = Ok (Appel 'POST' "/api/engagements/$($sansPiece.id)/decision" $comptable @{
    type = 'SUSPENSION'; observations = @('Joindre le bon de commande signé') })
Info "Statut : $($suspendu.statut). Observations groupées en une seule communication (art. 5) :"
$suspendu.decisions[-1].observations | ForEach-Object { Info " - $_" }

Titre '3. Le comptable vise le premier engagement : les crédits sont réservés'
$vise = Ok (Appel 'POST' "/api/engagements/$($engagement.id)/decision" $comptable @{ type = 'VISA' })
$ligneApres = Ok (Appel 'GET' "/api/lignes-budgetaires/$($ligne.id)" $comptable)
Info "Statut : $($vise.statut). Montant engagé sur la ligne : $($ligneApres.montantEngage) DH"

Titre "4. Le fournisseur dépose sa facture, puis tente de la déposer une seconde fois"
$depot = @{ engagementId = $engagement.id; numeroFacture = "F-$suffixe-A"; montantFacture = 60000; rib = $rib }
$dossier = Ok (Appel 'POST' '/api/dossiers' $fournisseur $depot)
Info "Dossier n° $($dossier.id) : facture $($dossier.numeroFacture) de $($dossier.montantFacture) DH, déposée le $($dossier.dateDepot)"
Refus (Appel 'POST' '/api/dossiers' $fournisseur $depot) 'DOUBLE_PAIEMENT'

Titre '5. Service fait, liquidation et ordonnancement'
Horloge '2026-03-12'
$d = "/api/dossiers/$($dossier.id)"
$null = Ok (Appel 'POST' "$d/service-fait" $ordonnateur)
$null = Ok (Appel 'POST' "$d/liquidation" $ordonnateur @{ montant = 60000 })
$ordonnance = Ok (Appel 'POST' "$d/ordonnancement" $ordonnateur)
Info "Ordonnance $($ordonnance.numeroOrdonnance) (série unique et ininterrompue, RGCP art. 36)"
Info "Échéances légales : ordonnancer avant le $($ordonnance.delais.echeanceOrdonnancement), payer avant le $($ordonnance.delais.echeancePaiement)"
Refus (Appel 'POST' "$d/controle" $ordonnateur @{ decision = 'VISER' }) $null
Info "(L'ordonnateur ne peut pas viser son propre paiement : rôle refusé.)"

Titre "6. Le comptable ne paie que le 26 mai : 15 jours après l'échéance"
Horloge '2026-05-26'
$enRetard = Ok (Appel 'GET' $d $comptable)
Info "Retard : $($enRetard.delais.joursDeRetard) jours. Intérêts moratoires courus : $($enRetard.interetsMoratoires) DH"
$paye = Ok (Appel 'POST' "$d/controle" $comptable @{ decision = 'VISER' })
Info "Statut : $($paye.statut) le $($paye.datePaiement) par $($paye.modeReglement)."
Info "Intérêts dus au fournisseur : $($paye.interetsMoratoires) DH = 60 000 x 15 jours x 3,4 % / 365 (décret 2-16-344, art. 11)"

Titre '7. Seconde facture : suspension, réquisition refusée puis acceptée'
Horloge '2026-05-27'
$dossier2 = Ok (Appel 'POST' '/api/dossiers' $fournisseur @{
    engagementId = $engagement.id; numeroFacture = "F-$suffixe-B"; montantFacture = 40000; rib = $rib })
$d2 = "/api/dossiers/$($dossier2.id)"
$null = Ok (Appel 'POST' "$d2/service-fait" $ordonnateur)
$null = Ok (Appel 'POST' "$d2/liquidation" $ordonnateur @{ montant = 40000 })
$null = Ok (Appel 'POST' "$d2/ordonnancement" $ordonnateur)
$null = Ok (Appel 'POST' "$d2/controle" $comptable @{
    decision = 'SUSPENDRE'; motifs = @('CREDITS_PAIEMENT_INSUFFISANTS'); note = 'Crédits de paiement non disponibles' })
Info 'Le comptable suspend pour insuffisance de crédits. L''ordonnateur requiert le paiement :'
Refus (Appel 'POST' "$d2/requisition" $ordonnateur @{ motif = 'Urgence de service' }) 'REQUISITION_IRRECEVABLE'
$null = Ok (Appel 'POST' "$d2/regularisation" $ordonnateur)
$null = Ok (Appel 'POST' "$d2/controle" $comptable @{
    decision = 'SUSPENDRE'; motifs = @('SIGNATURE_NON_QUALIFIEE'); note = 'Délégation de signature non transmise' })
Info 'Après régularisation, nouvelle suspension pour signature. Cette fois la réquisition est recevable :'
$null = Ok (Appel 'POST' "$d2/requisition" $ordonnateur @{ motif = 'Délégation signée, transmise par courrier' })
$requis = Ok (Appel 'POST' "$d2/paiement-sur-requisition" $comptable @{ modeReglement = 'VIREMENT' })
Info "Statut : $($requis.statut), payé sur réquisition : $($requis.payeSurRequisition) (responsabilité du comptable dégagée, art. 29)"

Titre '8. Tableau de bord et vérification du journal d''audit'
$tableau = Ok (Appel 'GET' '/api/pilotage?exercice=2026' $auditeur)
Info "Taux d'engagement : $($tableau.tauxEngagement) %. Taux de paiement : $($tableau.tauxPaiement) %"
Info "Délai moyen de paiement : $($tableau.delaiMoyenPaiementJours) jours. Payés dans le délai : $($tableau.partPayesDansLeDelai) %"
Info "Intérêts moratoires payés : $($tableau.interetsMoratoiresPayes) DH"
$integrite = Ok (Appel 'GET' '/api/audit/integrite' $auditeur)
Info "Journal d'audit : $($integrite.entreesVerifiees) entrées vérifiées, chaîne intacte : $($integrite.intacte)"

Write-Host ''
Write-Host 'Démonstration terminée.' -ForegroundColor Green
