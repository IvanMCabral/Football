import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LineupPlayerData } from './lineup-player-card.model';

@Component({
  selector: 'app-lineup-player-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lineup-player-card.component.html',
  styleUrl: './lineup-player-card.component.css'
})
export class LineupPlayerCardComponent {
  @Input() player!: LineupPlayerData;

  isSuspended(): boolean {
    return this.player.suspended === true || (this.player.suspensionRemainingMatches ?? 0) > 0;
  }

  isInjured(): boolean {
    return this.player.injured === true && !this.isSuspended();
  }

  energyPercent(): number {
    return this.clampEnergy(this.player.energy ?? 100);
  }

  isExhausted(): boolean {
    return this.energyPercent() <= 19;
  }

  isVeryTired(): boolean {
    const e = this.energyPercent();
    return e >= 20 && e <= 39;
  }

  isTired(): boolean {
    const e = this.energyPercent();
    return e >= 40 && e <= 59;
  }

  conditionWarningLabel(): string {
    if (this.isSuspended()) { return 'Suspended'; }
    if (this.isInjured()) { return 'Injured'; }
    if (this.isExhausted()) { return 'Exhausted'; }
    if (this.isVeryTired()) { return 'Very tired'; }
    if (this.isTired()) { return 'Tired'; }
    return '';
  }

  conditionWarningTooltip(): string {
    if (this.isSuspended()) {
      const remaining = this.player.suspensionRemainingMatches ?? 0;
      if (remaining > 0) {
        return `This player is suspended and unavailable for ${remaining} match(es).`;
      }
      return 'This player is suspended and cannot be selected.';
    }
    if (this.isInjured()) {
      return 'This player is injured. Consider replacing them before confirming the lineup.';
    }
    if (this.isExhausted()) {
      return 'This player is exhausted. Starting them may affect performance.';
    }
    if (this.isVeryTired()) {
      return 'This player is very tired. Consider resting them.';
    }
    if (this.isTired()) {
      return 'This player has reduced energy.';
    }
    return '';
  }

  conditionClass(): string {
    if (this.isSuspended()) { return 'condition-suspended'; }
    if (this.isInjured()) { return 'condition-injured'; }
    if (this.isExhausted()) { return 'condition-exhausted'; }
    if (this.isVeryTired()) { return 'condition-very-tired'; }
    if (this.isTired()) { return 'condition-tired'; }
    return '';
  }

  hasConditionWarning(): boolean {
    return this.isSuspended() || this.isInjured() || this.isExhausted() || this.isVeryTired() || this.isTired();
  }

  private clampEnergy(value: number | undefined | null): number {
    if (value === null || value === undefined) { return 100; }
    return Math.max(0, Math.min(100, Math.round(value)));
  }
}
