# Regles de progressió de Wildlife — Proposta provisional v0.1

**Estat:** Proposta provisional interna implementada; requereix revisió de producte abans de la beta tancada  
**Data:** 13 d’agost de 2026  
**Autoritat:** `Wildlife_prd.md` continua sent el document de referència. Aquest fitxer explicita les regles de progressió incompletes i permet editar-les fàcilment.  
**Document original:** [`progression_rules.md`](progression_rules.md)

> Aquesta versió està preparada per a la revisió en català. Les claus tècniques i els valors numèrics coincideixen amb el document original perquè els comentaris es puguin traslladar sense ambigüitats.

**Implementació:** `ProgressionRules.kt` és la configuració centralitzada d’execució d’aquesta versió. L’esquema SQLite v4 conserva les claus i els punts existents i hi afegeix metadades tipades per a l’historial. El perfil mostra el nivell projectat, el progrés, les recompenses recents i els títols aconseguits seleccionables. Les mecàniques desactivades en aquest document no es mostren ni concedeixen recompenses.

## 1. Com revisar aquesta proposta

Totes les decisions ajustables es concentren a les taules de les seccions 3–6. La persona revisora ha de modificar els valors d’aquestes taules i anotar la decisió a la secció 10. Les seccions narratives defineixen el comportament de seguretat i de dades i només s’haurien de canviar quan canviï el contracte de producte corresponent.

Les regles utilitzen claus estables com ara `confirmed_observation` i `field_ranger`; els textos de la interfície es poden traduir o canviar de nom sense modificar els esdeveniments emmagatzemats. La implementació ha de mantenir la versió de les regles separada del registre d’XP perquè els llindars es puguin canviar sense reescriure l’XP aconseguida.

## 2. Regles no negociables

- Només pot obtenir progressió l’identificador immutable i verificat de l’usuari d’iNaturalist.
- Wildlife continua sent de només lectura; cap mecànica de progressió pot requerir una via d’escriptura a iNaturalist que no estigui documentada.
- Els esdeveniments d’XP només es poden afegir i han de ser idempotents. Un reintent no pot concedir mai dues vegades el mateix esdeveniment.
- L’XP no es resta mai després d’un canvi d’identificació o de taxonomia.
- Les projeccions de la col·lecció poden canviar, però l’XP registrada continua sent un fet històric.
- Els nivells i les recompenses són cosmètics. No poden modificar la visibilitat d’una observació, l’estat científic, la confiança d’una coincidència ni l’accés a informació biològica.
- La raresa, la verificació i l’estat d’observació continuen sent conceptes separats.
- Les observacions històriques importades durant la sincronització inicial desbloquegen la col·lecció, però en aquesta versió provisional no concedeixen XP retroactiva.
- Un traspàs ambigu no concedeix res fins que l’usuari confirma explícitament l’observació pública coincident.

## 3. Configuració dels esdeveniments d’XP

Aquestes claus i aquests valors són la font editable de la implementació provisional.

| Clau de l’esdeveniment | Activador | XP | Actiu | Notes |
|---|---|---:|---|---|
| `confirmed_observation` | Una observació pública coincident es confirma explícitament a Wildlife | 10 | Sí | Coincideix amb el registre actual |
| `first_species` | Primer tàxon de col·lecció confirmat a nivell d’espècie de l’usuari | 500 | Sí | Coincideix amb el registre actual; clau basada en l’ID del tàxon de col·lecció |
| `research_grade` | Una observació pública ja coneguda arriba per primera vegada a Grau de recerca | 50 | No | Activar només quan s’hagin implementat la sincronització de transicions i la idempotència |
| `identification_given` | Una identificació vàlida aportada a un altre usuari d’iNaturalist | 25 | No | v1.2; requereix camps d’origen validats, excloure les autoidentificacions i un límit diari |
| `anomaly_confirmed` | Una observació fora de distribució supera la revisió i el retard requerits | 250 | No | Requereix un indicador de distribució versionat; no s’ha d’inferir només del Grau de recerca |

### 3.1 Regla per a observacions repetides

Actualment, l’aplicació concedeix `confirmed_observation` una vegada per cada observació pública confirmada. Per a la versió provisional de la progressió, s’aplica la reducció següent per tàxon de col·lecció a nivell d’espècie i setmana ISO:

| Observació confirmada de la mateixa espècie durant una setmana ISO | XP base de l’observació |
|---:|---:|
| 1a | 10 |
| 2a | 5 |
| 3a | 5 |
| 4a i següents | 0 |

`first_species` i un futur esdeveniment `research_grade` són independents d’aquesta reducció. Una observació sense identificar o identificada només fins al gènere pot concedir l’XP base d’observació, però només concedeix `first_species` si una sincronització posterior proporciona un tàxon de col·lecció a nivell d’espècie i encara no existeix l’esdeveniment corresponent de primera espècie.

### 3.2 Configuració de la raresa

Els multiplicadors de raresa estan desactivats mentre el catàleg i el model de raresa estacional siguin provisionals.

| Categoria de raresa | Multiplicador proposat sobre l’XP base | Actiu |
|---|---:|---|
| Comuna | ×1 | No |
| Poc comuna | ×5 | No |
| Rara | ×20 | No |
| Llegendària | ×50 | No |

Abans d’activar aquests valors, cal validar les regles del catàleg estacional i simular si les recompenses de raresa dominen excessivament la progressió per primeres espècies. La freqüència bruta d’observacions no es pot presentar com a raresa biològica.

## 4. Configuració dels nivells

El nivell és una projecció de l’XP total registrada durant tota la vida del compte: el nivell de l’usuari és el llindar més alt inferior o igual a la seva XP total.

| Clau del nivell | Nom visible proposat | Llindar d’XP total | Fites aproximades de primeres espècies | Recompensa cosmètica |
|---|---|---:|---:|---|
| `tourist` | Turista | 0 | 0 | Títol de perfil predeterminat |
| `explorer` | Explorador/a | 500 | 1 | Títol «Explorador/a» seleccionable |
| `naturalist` | Naturalista | 2.500 | 5 | Títol «Naturalista» seleccionable |
| `tracker` | Rastrejador/a | 7.500 | 15 | Títol «Rastrejador/a» seleccionable |
| `field_ranger` | Guarda de camp | 20.000 | 40 | Títol «Guarda de camp» seleccionable |
| `master_ranger` | Guarda expert/a | 50.000 | 99 | Títol «Guarda expert/a» seleccionable |
| `legendary_ranger` | Guarda llegendari/ària | 100.000 | 197 | Títol «Guarda llegendari/ària» seleccionable |

La columna de fites només és explicativa. Els nivells es calculen a partir de l’XP, no del nombre d’espècies. Els noms catalans també són provisionals i s’han de revisar lingüísticament.

### 4.1 Càlcul del progrés

Per a tots els nivells excepte l’últim:

```text
progrés = (XP total - llindar actual) / (llindar següent - llindar actual)
```

El progrés es limita a l’interval `0…1`. Al nivell més alt, es mostra l’XP total acumulada sense inventar un objectiu següent.

### 4.2 Exemples de ritme de la proposta

Suposant una observació recompensada per cada espècie nova i cap altra font d’XP:

| Espècies noves confirmades | XP aproximada | Nivell resultant |
|---:|---:|---|
| 0 | 0 | Turista |
| 1 | 510 | Explorador/a |
| 5 | 2.550 | Naturalista |
| 15 | 7.650 | Rastrejador/a |
| 40 | 20.400 | Guarda de camp |
| 99 | 50.490 | Guarda expert/a |
| 197 | 100.470 | Guarda llegendari/ària |

Abans de la beta, aquests exemples s’han de simular amb comptes representatius petits, mitjans i molt actius.

## 5. Recompenses i desbloquejos

La proposta provisional només concedeix títols de perfil. L’usuari pot mostrar qualsevol títol del nivell més alt que hagi assolit o d’un nivell inferior.

| Tipus de recompensa | Regla provisional |
|---|---|
| Títol de perfil | Actiu; un títol per nivell assolit |
| Temes o paletes de colors | Desactivats |
| Accés especial al catàleg | Prohibit; la informació biològica no es bloqueja mai per nivell |
| Potenciadors o multiplicadors d’XP | Prohibits |
| Avantatge competitiu | Prohibit |
| Recompensa física o monetària | Fora d’abast |

Si els llindars canvien després de l’inici de la beta, un compte no pot perdre un títol que ja havia assolit. Abans d’augmentar cap llindar, cal emmagatzemar o derivar `highest_level_achieved` durant la migració.

## 6. Configuració de medalles i ratxes

Les medalles i les ratxes continuen desactivades per a l’MVP, d’acord amb l’abast de la versió v1.1 del full de ruta. Les propostes següents són candidates per revisar, no compromisos d’implementació:

| Clau candidata | Requisit proposat | Activa | Definició pendent |
|---|---|---|---|
| `first_field_note` | 1 observació pública confirmada | No | Revisió de la imatge i el text de la medalla |
| `ten_species` | 10 espècies confirmades diferents | No | Decidir si compten les entrades històriques de la col·lecció |
| `fifty_species` | 50 espècies confirmades diferents | No | Decidir si compten les entrades històriques de la col·lecció |
| `research_contributor` | 10 observacions arriben a Grau de recerca | No | Sincronització de transicions a Grau de recerca |
| `night_owl` | 5 observacions nocturnes vàlides | No | Definició basada en l’hora solar local; l’hora del rellotge no és suficient |
| `biome_master` | 50% d’un catàleg regional congelat | No | Catàleg congelat i denominador de bioma o regió |

En aquesta proposta, les medalles són cosmètiques i concedeixen `0 XP`. Les ratxes també concedeixen `0 XP`; cal revisar-ne la recurrència, el període de gràcia i el comportament amb els fusos horaris abans d’implementar-les.

## 7. Identitat i cicle de vida dels esdeveniments

Claus d’idempotència proposades:

```text
observation:<UUID de l’observació>
first_species:<ID del tàxon de col·lecció>
research_grade:<UUID de l’observació>
identification_given:<ID de la identificació>
anomaly_confirmed:<UUID de l’observació>:<versió de les regles de distribució>
```

`observation:<UUID>` és la clau que ja s’emmagatzema per a l’esdeveniment lògic `confirmed_observation` i s’ha de mantenir estable; canviar-ne el nom podria duplicar recompenses durant una actualització.

- Tornar a enllaçar el mateix ID immutable d’usuari d’iNaturalist recupera el registre existent.
- Enllaçar un ID d’usuari diferent mostra la progressió independent d’aquell usuari.
- Desenllaçar el compte oculta la progressió personal, però no l’esborra silenciosament; l’eliminació explícita de dades locals és una acció de privacitat separada.
- Un canvi taxonòmic pot modificar la posició dins de la col·lecció, però no elimina esdeveniments d’XP antics.
- Una actualització de les regles recalcula els nivells a partir de l’XP total, però no modifica els imports dels esdeveniments existents.

## 8. Contracte de la interfície provisional

La primera interfície de progressió pot mostrar:

- el nom del nivell actual;
- l’XP total acumulada;
- el progrés fins al nivell següent;
- l’XP que falta per al nivell següent;
- els esdeveniments recents del registre amb l’origen explicat amb llenguatge clar;
- el títol de perfil seleccionable entre els que s’han assolit.

No pot mostrar medalles, multiplicadors de raresa, ratxes ni recompenses «properament» desactivades com si fossin funcionalitats ja obtingudes. Cal incloure els estats de càrrega, buit, compte no enllaçat i error recuperable.

## 9. Política de versions i revisions

| Camp | Valor provisional |
|---|---|
| Versió de les regles | `progression-0.1-placeholder` |
| Públic previst | Desenvolupament intern i proves en dispositiu abans de la beta |
| Garantia d’estabilitat dels llindars | Cap abans de la beta tancada |
| Es permet reescriure el registre d’XP | Mai |
| Conservació del nivell màxim | Obligatòria des de l’inici de la beta tancada |
| Data límit de revisió | Abans de congelar les regles de progressió per a la beta tancada |

La implementació ha de centralitzar els esdeveniments actius, els valors d’XP i els llindars en un únic objecte de configuració cobert per proves unitàries. El codi de la interfície ha de consumir la projecció resultant i no duplicar números ni noms de nivell.

## 10. Registre de revisions

| Data | Versió de les regles | Persona revisora | Resum de la decisió |
|---|---|---|---|
| 13 d’agost de 2026 | `progression-0.1-placeholder` | Proposta de Codex | Proposta provisional editable inicial; pendent de revisió de producte |
