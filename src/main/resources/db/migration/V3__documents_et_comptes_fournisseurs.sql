-- Rattache un compte de connexion au fournisseur qu'il représente : un fournisseur ne voit et ne
-- facture que ses propres commandes.
CREATE TABLE comptes_fournisseurs (
    utilisateur             varchar(100) PRIMARY KEY,
    fournisseur_identifiant varchar(30)  NOT NULL,
    fournisseur_nom         varchar(200) NOT NULL
);
CREATE INDEX comptes_fournisseurs_identifiant ON comptes_fournisseurs (fournisseur_identifiant);

-- Pièces justificatives d'un engagement ou d'un dossier de paiement. Elles ne sont ni modifiées ni
-- supprimées : une pièce erronée est complétée par une nouvelle pièce, et l'empreinte en prouve
-- l'intégrité.
CREATE TABLE documents (
    id          bigserial PRIMARY KEY,
    type_objet  varchar(20)  NOT NULL CHECK (type_objet IN ('ENGAGEMENT', 'DOSSIER')),
    id_objet    bigint       NOT NULL,
    nature      varchar(30)  NOT NULL CHECK (nature IN ('FACTURE', 'ATTESTATION_SERVICE_FAIT', 'BON_DE_COMMANDE',
                                 'MARCHE', 'CONTRAT', 'RIB', 'AUTRE')),
    nom_fichier varchar(255) NOT NULL,
    type_contenu varchar(100) NOT NULL,
    taille      integer      NOT NULL CHECK (taille > 0),
    empreinte   char(64)     NOT NULL,
    contenu     bytea        NOT NULL,
    depose_par  varchar(100) NOT NULL,
    depose_le   timestamptz  NOT NULL
);
CREATE INDEX documents_objet ON documents (type_objet, id_objet);
