export interface PlayerCardData {
  sessionPlayerId?: string;
  playerId?: string;
  name: string;
  position: string;
  attack: number;
  defense: number;
  technique: number;
  speed: number;
  age: number;
  form?: number;
  energy: number;
  injured?: boolean;
  injuryType?: string | null;
  injuryRemainingMatches?: number | null;
  yellowCards?: number;
  redCards?: number;
  suspended?: boolean;
  suspensionRemainingMatches?: number;
}
