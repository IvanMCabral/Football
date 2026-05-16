import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerCardData } from './player-card.model';

@Component({
  selector: 'app-player-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './player-card.component.html',
  styleUrl: './player-card.component.css'
})
export class PlayerCardComponent {
  @Input() player!: PlayerCardData;
  @Input() isSquad: boolean = false;

  isSuspended(): boolean {
    return this.player.suspended === true || (this.player.suspensionRemainingMatches ?? 0) > 0;
  }

  suspendedLabel(): string {
    return 'Suspended';
  }

  suspendedDetail(): string {
    const remaining = this.player.suspensionRemainingMatches ?? 0;
    if (remaining > 0) {
      return remaining === 1 ? 'Unavailable for 1 match' : `Unavailable for ${remaining} matches`;
    }
    return 'Unavailable';
  }

  suspendedTooltip(): string {
    const remaining = this.player.suspensionRemainingMatches ?? 0;
    if (remaining > 0) {
      return `Player is suspended for ${remaining} match(es) and cannot be selected`;
    }
    return 'Player is suspended and cannot be selected';
  }

  isInjured(): boolean {
    return this.player.injured === true && !this.isSuspended();
  }

  injuryLabel(): string {
    if (this.player.injured !== true) { return ''; }
    const remaining = this.player.injuryRemainingMatches;
    if (remaining === null || remaining === undefined || remaining <= 0) {
      return 'Injured';
    }
    return remaining === 1 ? 'Returning soon' : 'Injured';
  }

  injuryDetail(): string {
    if (this.player.injured !== true) { return ''; }
    const remaining = this.player.injuryRemainingMatches;
    if (remaining === null || remaining === undefined || remaining <= 0) {
      return 'Unavailable';
    }
    return remaining === 1 ? 'Out 1 match' : `Out ${remaining} matches`;
  }

  injuryTooltip(): string {
    if (this.player.injured !== true) { return ''; }
    const label = this.injuryLabel();
    const detail = this.injuryDetail();
    if (this.player.injuryType) {
      return `${label}: ${this.player.injuryType} — ${detail}`;
    }
    return `${label} — ${detail}`;
  }

  energyStatus(): 'fresh' | 'good' | 'tired' | 'very-tired' | 'exhausted' {
    const e = this.clampEnergy(this.player.energy ?? 100);
    if (e >= 80) { return 'fresh'; }
    if (e >= 60) { return 'good'; }
    if (e >= 40) { return 'tired'; }
    if (e >= 20) { return 'very-tired'; }
    return 'exhausted';
  }

  energyLabel(): string {
    const labels: Record<string, string> = {
      'fresh': 'Fresh',
      'good': 'Good',
      'tired': 'Tired',
      'very-tired': 'Very Tired',
      'exhausted': 'Exhausted'
    };
    return labels[this.energyStatus()] ?? '';
  }

  energyTooltip(): string {
    return `Energy level: ${this.energyPercent()}% — ${this.energyLabel()}`;
  }

  energyPercent(): number {
    return this.clampEnergy(this.player.energy ?? 100);
  }

  private clampEnergy(value: number | undefined | null): number {
    if (value === null || value === undefined) { return 100; }
    return Math.max(0, Math.min(100, Math.round(value)));
  }
}
