-- Schéma de Sarf. Les règles essentielles sont aussi garanties par la base : même si le code avait
-- un défaut, une facture ne pourrait pas être déposée deux fois et le journal resterait intact.

CREATE TABLE jours_feries (
    jour    date PRIMARY KEY,
    libelle varchar(120) NOT NULL
);

CREATE TABLE services_ordonnateurs (
    code    varchar(20) PRIMARY KEY,
    libelle varchar(200) NOT NULL,
    -- Décret n° 2-07-1235, art. 2 et 12 : contrôle complet ou contrôle modulé.
    regime  varchar(10)  NOT NULL CHECK (regime IN ('NORMAL', 'MODULE'))
);

-- RGCP, art. 5 : les ordonnateurs sont accrédités auprès du comptable assignataire.
CREATE TABLE accreditations (
    utilisateur  varchar(100) NOT NULL,
    service_code varchar(20)  NOT NULL REFERENCES services_ordonnateurs (code),
    accredite_le timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (utilisateur, service_code)
);

CREATE TABLE lignes_budgetaires (
    id              bigserial PRIMARY KEY,
    exercice        integer       NOT NULL,
    imputation      varchar(40)   NOT NULL,
    libelle         varchar(200)  NOT NULL,
    nature          varchar(20)   NOT NULL CHECK (nature IN ('FONCTIONNEMENT', 'INVESTISSEMENT')),
    credits_ouverts numeric(15,2) NOT NULL CHECK (credits_ouverts >= 0),
    montant_engage  numeric(15,2) NOT NULL DEFAULT 0,
    montant_paye    numeric(15,2) NOT NULL DEFAULT 0,
    version         integer       NOT NULL DEFAULT 0,
    UNIQUE (exercice, imputation),
    CONSTRAINT engage_dans_les_credits CHECK (montant_engage BETWEEN 0 AND credits_ouverts),
    CONSTRAINT paye_dans_l_engage CHECK (montant_paye BETWEEN 0 AND montant_engage)
);

CREATE TABLE engagements (
    id                      bigserial PRIMARY KEY,
    exercice                integer       NOT NULL,
    service_code            varchar(20)   NOT NULL REFERENCES services_ordonnateurs (code),
    regime                  varchar(10)   NOT NULL CHECK (regime IN ('NORMAL', 'MODULE')),
    ligne_budgetaire_id     bigint        NOT NULL REFERENCES lignes_budgetaires (id),
    imputation              varchar(40)   NOT NULL,
    ordonnateur             varchar(100)  NOT NULL,
    objet                   varchar(300)  NOT NULL,
    type_commande           varchar(30)   NOT NULL
        CHECK (type_commande IN ('MARCHE', 'BON_DE_COMMANDE', 'CONTRAT_DROIT_COMMUN')),
    reference_piece         varchar(60),
    fournisseur_nom         varchar(200)  NOT NULL,
    fournisseur_identifiant varchar(30)   NOT NULL,
    montant_propose         numeric(15,2) NOT NULL CHECK (montant_propose > 0),
    somme_a_valoir          numeric(15,2) NOT NULL CHECK (somme_a_valoir >= 0),
    montant_liquide         numeric(15,2) NOT NULL DEFAULT 0,
    statut                  varchar(20)   NOT NULL CHECK (statut IN ('SOUMIS', 'VISE', 'SUSPENDU', 'REFUSE')),
    date_depot              date          NOT NULL,
    echeance_visa           date          NOT NULL,
    version                 integer       NOT NULL DEFAULT 0,
    -- RGCP, art. 33 : on ne liquide pas au-delà de ce qui a été engagé.
    CONSTRAINT liquidation_dans_l_engagement CHECK (montant_liquide BETWEEN 0 AND montant_propose)
);
CREATE INDEX engagements_statut ON engagements (statut);

CREATE TABLE engagement_lignes (
    engagement_id bigint        NOT NULL REFERENCES engagements (id) ON DELETE CASCADE,
    rang          integer       NOT NULL,
    designation   varchar(200)  NOT NULL,
    quantite      numeric(12,3) NOT NULL CHECK (quantite > 0),
    prix_unitaire numeric(15,2) NOT NULL CHECK (prix_unitaire >= 0),
    PRIMARY KEY (engagement_id, rang)
);

CREATE TABLE engagement_decisions (
    engagement_id bigint       NOT NULL REFERENCES engagements (id) ON DELETE CASCADE,
    rang          integer      NOT NULL,
    type          varchar(20)  NOT NULL CHECK (type IN ('VISA', 'SUSPENSION', 'REFUS')),
    date_decision date         NOT NULL,
    comptable     varchar(100) NOT NULL,
    observations  text[]       NOT NULL DEFAULT '{}',
    PRIMARY KEY (engagement_id, rang)
);

CREATE TABLE dossiers (
    id                        bigserial PRIMARY KEY,
    engagement_id             bigint        NOT NULL REFERENCES engagements (id),
    exercice                  integer       NOT NULL,
    service_code              varchar(20)   NOT NULL,
    ordonnateur               varchar(100)  NOT NULL,
    ligne_budgetaire_id       bigint        NOT NULL REFERENCES lignes_budgetaires (id),
    imputation                varchar(40)   NOT NULL,
    creancier_nom             varchar(200)  NOT NULL,
    creancier_identifiant     varchar(30)   NOT NULL,
    creancier_rib             varchar(40),
    numero_facture            varchar(60)   NOT NULL,
    montant_facture           numeric(15,2) NOT NULL CHECK (montant_facture > 0),
    retenue_garantie          numeric(15,2) NOT NULL DEFAULT 0,
    date_depot                date          NOT NULL,
    deposant                  varchar(100)  NOT NULL,
    date_certification        date,
    date_service_fait         date,
    montant_liquide           numeric(15,2) NOT NULL DEFAULT 0,
    numero_ordonnance         varchar(40)   UNIQUE,
    date_ordonnancement       date,
    date_transmission         date,
    jours_comptable_consommes integer       NOT NULL DEFAULT 0,
    statut                    varchar(20)   NOT NULL CHECK (statut IN ('DEPOSE', 'SERVICE_FAIT', 'LIQUIDE',
                                  'ORDONNANCE', 'SUSPENDU', 'REQUISITIONNE', 'PAYE')),
    requisition_date          date,
    requisition_par           varchar(100),
    requisition_motif         varchar(1000),
    date_paiement             date,
    mode_reglement            varchar(10)   CHECK (mode_reglement IN ('VIREMENT', 'ESPECES')),
    interets_moratoires       numeric(15,2) NOT NULL DEFAULT 0,
    version                   integer       NOT NULL DEFAULT 0,
    -- Décret n° 2-07-1235, art. 12 : une même créance ne peut pas être payée deux fois.
    CONSTRAINT facture_unique UNIQUE (creancier_identifiant, numero_facture),
    CONSTRAINT retenue_dans_la_facture CHECK (retenue_garantie BETWEEN 0 AND montant_facture),
    CONSTRAINT liquidation_dans_la_facture CHECK (montant_liquide BETWEEN 0 AND montant_facture),
    CONSTRAINT paiement_date CHECK ((statut = 'PAYE') = (date_paiement IS NOT NULL))
);
CREATE INDEX dossiers_statut ON dossiers (statut);
CREATE INDEX dossiers_exercice ON dossiers (exercice);

CREATE TABLE dossier_suspensions (
    dossier_id bigint        NOT NULL REFERENCES dossiers (id) ON DELETE CASCADE,
    rang       integer       NOT NULL,
    debut      date          NOT NULL,
    fin        date,
    motif      varchar(1000) NOT NULL,
    PRIMARY KEY (dossier_id, rang),
    CHECK (fin IS NULL OR fin >= debut)
);

CREATE TABLE dossier_decisions (
    dossier_id    bigint        NOT NULL REFERENCES dossiers (id) ON DELETE CASCADE,
    rang          integer       NOT NULL,
    type          varchar(30)   NOT NULL CHECK (type IN ('VISA_ET_REGLEMENT', 'SUSPENSION', 'VISA_SUR_REQUISITION')),
    date_decision date          NOT NULL,
    comptable     varchar(100)  NOT NULL,
    motifs        text[]        NOT NULL DEFAULT '{}',
    note          varchar(2000),
    PRIMARY KEY (dossier_id, rang)
);

-- RGCP, art. 36 : numérotation unique et ininterrompue par ordonnateur et par année.
CREATE TABLE compteurs_ordonnances (
    service_code varchar(20) NOT NULL,
    exercice     integer     NOT NULL,
    dernier      integer     NOT NULL CHECK (dernier > 0),
    PRIMARY KEY (service_code, exercice)
);

-- Décret n° 2-16-344, art. 11 : un taux par trimestre, publié par le Trésorier général du Royaume.
CREATE TABLE taux_interets_moratoires (
    annee     integer      NOT NULL,
    trimestre integer      NOT NULL CHECK (trimestre BETWEEN 1 AND 4),
    taux      numeric(5,2) NOT NULL CHECK (taux >= 0),
    source    varchar(300) NOT NULL,
    PRIMARY KEY (annee, trimestre)
);

-- Journal d'audit chaîné : chaque entrée contient l'empreinte SHA-256 de la précédente.
CREATE TABLE journal_audit (
    id                   bigserial PRIMARY KEY,
    horodatage           timestamptz  NOT NULL,
    acteur               varchar(100) NOT NULL,
    action               varchar(60)  NOT NULL,
    type_objet           varchar(40)  NOT NULL,
    id_objet             varchar(40)  NOT NULL,
    details              text,
    empreinte_precedente char(64)     NOT NULL,
    empreinte            char(64)     NOT NULL UNIQUE
);

-- Ajout seul : toute modification, suppression ou purge du journal est refusée par la base.
CREATE FUNCTION journal_audit_immuable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Le journal d''audit est en ajout seul : % interdit', TG_OP;
END;
$$;

CREATE TRIGGER journal_audit_sans_modification
    BEFORE UPDATE OR DELETE ON journal_audit
    FOR EACH ROW EXECUTE FUNCTION journal_audit_immuable();

CREATE TRIGGER journal_audit_sans_purge
    BEFORE TRUNCATE ON journal_audit
    FOR EACH STATEMENT EXECUTE FUNCTION journal_audit_immuable();
