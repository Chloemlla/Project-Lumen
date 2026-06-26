import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/core/enums/pomodoro_phase.dart';
import 'package:project_lumen/features/pomodoro/application/pomodoro_controller.dart';
import 'package:project_lumen/features/pomodoro/presentation/widgets/pomodoro_cycle_bar.dart';
import 'package:project_lumen/features/pomodoro/presentation/widgets/pomodoro_timer_card.dart';
import 'package:project_lumen/shared/widgets/app_action_group.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class PomodoroPage extends ConsumerWidget {
  const PomodoroPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(pomodoroControllerProvider);

    return AppScaffold(
      title: '番茄钟',
      location: '/pomodoro',
      body: StreamBuilder<int>(
        stream: Stream<int>.periodic(
          const Duration(seconds: 1),
          (value) => value,
        ).asBroadcastStream(),
        builder: (context, _) {
          final now = DateTime.now();
          final remaining = state.phaseEndAt == null
              ? Duration.zero
              : state.phaseEndAt!.difference(now);
          final safeRemaining = remaining.isNegative
              ? Duration.zero
              : remaining;

          return ListView(
            children: [
              PomodoroTimerCard(
                title: _labelForPhase(state.phase),
                remaining: safeRemaining,
              ),
              const SizedBox(height: 16),
              PomodoroCycleBar(cycleIndex: state.cycleIndex.clamp(1, 4)),
              const SizedBox(height: 16),
              AppActionGroup(
                actions: [
                  AppAction(
                    label: state.phase == PomodoroPhase.awaitingFocusConfirm
                        ? '开始专注'
                        : '启动番茄',
                    icon: Icons.play_arrow_rounded,
                    onPressed:
                        state.phase == PomodoroPhase.idle ||
                            state.phase == PomodoroPhase.awaitingFocusConfirm
                        ? () {
                            if (state.phase ==
                                PomodoroPhase.awaitingFocusConfirm) {
                              ref
                                  .read(pomodoroControllerProvider.notifier)
                                  .confirmFocusStart();
                              return;
                            }
                            ref.read(pomodoroControllerProvider.notifier).start();
                          }
                        : null,
                  ),
                  AppAction(
                    label: '结束当前阶段',
                    icon: Icons.fast_forward_rounded,
                    onPressed:
                        state.phase == PomodoroPhase.focus || state.isBreak
                        ? () {
                            ref
                                .read(pomodoroControllerProvider.notifier)
                                .handlePhaseEnd();
                          }
                        : null,
                    style: AppActionStyle.outlined,
                  ),
                  AppAction(
                    label: '跳过休息',
                    icon: Icons.skip_next_rounded,
                    onPressed: state.isBreak
                        ? () {
                            ref
                                .read(pomodoroControllerProvider.notifier)
                                .skipBreak();
                          }
                        : null,
                    style: AppActionStyle.outlined,
                  ),
                  AppAction(
                    label: '停止',
                    icon: Icons.stop_rounded,
                    onPressed: state.phase == PomodoroPhase.idle
                        ? null
                        : () {
                            ref
                                .read(pomodoroControllerProvider.notifier)
                                .stop();
                          },
                    style: AppActionStyle.outlined,
                  ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }

  String _labelForPhase(PomodoroPhase phase) {
    return switch (phase) {
      PomodoroPhase.idle => '尚未开始',
      PomodoroPhase.awaitingFocusConfirm => '等待开始专注',
      PomodoroPhase.focus => '专注中',
      PomodoroPhase.shortBreak => '短休息',
      PomodoroPhase.longBreak => '长休息',
    };
  }
}
