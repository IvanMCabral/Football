# MVP 1 Goalkeeper and Squad Coverage Review

Date: 2026-07-30

## Scope

This review closes the squad coverage finding from the independent MVP 1 release dataset audit: every playable club must have at least two real goalkeepers while preserving the MVP 1 roster size and player identity policy.

## Dataset invariant

- Countries: 3
- Leagues: 3
- Clubs with roster files: 70
- Players per club: 24
- Total players: 1680
- Special traits per player: 2
- Total special traits: 3360
- Minimum goalkeepers per club: 2

## Corrections applied

The correction did not add duplicated identities and did not pad squads with fictional goalkeepers. Players already present in the source roster but incorrectly typed as field players were corrected to `GK`. Field players previously marked as `GK` were moved back to their real field role when a club already had the required real goalkeeper coverage.

| Country | Club | Corrected goalkeeper identities |
| --- | --- | --- |
| Argentina | Aldosivi | Axel Werner |
| Argentina | Barracas Central | Juan Espínola |
| Argentina | Boca Juniors | Javier García, Leandro Brey |
| Argentina | Defensa y Justicia | Lautaro Amadé |
| Argentina | Deportivo Riestra | Ignacio Arce |
| Argentina | Estudiantes LP | Fabricio Iacovich |
| Argentina | Estudiantes RC | Francisco Gualtieri |
| Argentina | Gimnasia Mendoza | César Rigamonti |
| Argentina | Godoy Cruz | Franco Petroli |
| Argentina | Independiente Rivadavia | Fernando Bravo, Kevin Pagliaroli |
| Argentina | River Plate | Ezequiel Centurión |
| Argentina | Rosario Central | Conan Ledesma, Ezequiel Zapata |
| Brazil | Atlético Mineiro | Gabriel Delfim |
| Brazil | Grêmio | Gabriel Grando |
| Brazil | São Paulo | Carlos Coronel, João Pedro |
| Brazil | Vitória | Lucas Arcanjo, Fintelman |
| Spain | Alavés | Jesús Owono |
| Spain | Elche | Matías Dituro |
| Spain | Espanyol | Marko Dmitrović, Ángel Fortuño |
| Spain | Girona | Vladyslav Krapyvtsov, Sergi Puig |
| Spain | Levante | Pablo Cuñat |
| Spain | Mallorca | Iván Cuéllar, Lucas Bergström, Leo Román |

## Field-player position corrections

The following players were previously marked as `GK` but are not goalkeepers in the accepted roster model. They were moved to field positions so the tactical and auto-select flows do not treat them as valid goalkeepers:

| Club | Player | Corrected position |
| --- | --- | --- |
| Rosario Central | Agustín Sández | LB |
| Grêmio | Caio Paulista | LB |
| Vitória | Cacá | CB |
| São Paulo | Enzo Díaz | LB |
| Espanyol | Clemens Riedel | CB |
| Girona | Alejandro Francés | CB |
| Mallorca | Antonio Raíllo | CB |

## Trait compatibility corrections

All corrected `GK` players now use goalkeeper-compatible traits only:

- `one_on_one_keeper`
- `sweeper_keeper`

The field players moved out of `GK` were also assigned field-compatible traits so import validation cannot accept contradictory player profiles.

## Source evidence used

The review used the current roster pages and player profiles available on 2026-07-30, including ESPN roster pages for Defensa y Justicia, Estudiantes RC, Independiente Rivadavia, São Paulo, Rosario Central, Vitória and Espanyol; Transfermarkt/Goal/FOX roster pages for Girona, Espanyol and São Paulo; and player profile pages for confirmed goalkeeper identities.

## Validation

Local dataset validation after the correction:

```text
players=1680
specialTraits=3360
clubsWithInvalidRosterSize=0
clubsWithLessThanTwoGoalkeepers=0
playersWithInvalidPosition=0
playersWithoutExactlyTwoTraits=0
```

## Result

The MVP 1 source dataset now satisfies the goalkeeper and squad coverage acceptance invariant without changing the expected total player count.
