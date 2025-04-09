import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../services/websocket_service.dart';
import '../models/task.dart';
import '../widgets/task_card.dart';
import '../widgets/create_task_dialog.dart';

/// Main task board screen showing tasks in a Kanban-style column layout.
///
/// Connects to the WebSocket on mount and receives real-time task updates.
/// Each Kanban column corresponds to a [TaskStatus] value.
class TaskBoardScreen extends StatefulWidget {
  const TaskBoardScreen({super.key});

  @override
  State<TaskBoardScreen> createState() => _TaskBoardScreenState();
}

class _TaskBoardScreenState extends State<TaskBoardScreen> {
  // Demo project ID — in a real app this would come from a project selector
  static const _projectId = '00000000-0000-0000-0002-000000000001';

  List<Task> _tasks = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadTasks();
    _connectWebSocket();
  }

  Future<void> _loadTasks() async {
    final auth = context.read<AuthService>();
    final api = context.read<ApiService>();

    try {
      final tasks = await api.getTasks(
        token: auth.token!,
        projectId: _projectId,
      );
      if (mounted) setState(() { _tasks = tasks; _isLoading = false; });
    } on ApiException catch (e) {
      if (mounted) setState(() { _error = e.message; _isLoading = false; });
    }
  }

  void _connectWebSocket() {
    final ws = context.read<WebSocketService>();
    ws.connect(_projectId);
    // Sync WebSocket task list with HTTP-fetched tasks
    ws.addListener(() {
      if (mounted) setState(() => _tasks = List.from(ws.tasks));
    });
  }

  Future<void> _createTask(String title, String? description) async {
    final auth = context.read<AuthService>();
    final api = context.read<ApiService>();

    try {
      final newTask = await api.createTask(
        token: auth.token!,
        title: title,
        projectId: _projectId,
        description: description,
      );
      // WebSocket event will update the list — add locally as optimistic update
      setState(() => _tasks.add(newTask));
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to create task: ${e.message}')),
        );
      }
    }
  }

  Future<void> _updateTaskStatus(Task task, TaskStatus newStatus) async {
    final auth = context.read<AuthService>();
    final api = context.read<ApiService>();

    // Optimistic update — update UI immediately, revert on error
    setState(() {
      final index = _tasks.indexWhere((t) => t.id == task.id);
      if (index != -1) _tasks[index] = task.copyWith(status: newStatus);
    });

    try {
      await api.updateTask(
        token: auth.token!,
        taskId: task.id,
        status: newStatus.value,
      );
    } on ApiException catch (e) {
      // Revert optimistic update on failure
      setState(() {
        final index = _tasks.indexWhere((t) => t.id == task.id);
        if (index != -1) _tasks[index] = task;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Update failed: ${e.message}')),
        );
      }
    }
  }

  void _logout() {
    context.read<WebSocketService>().disconnect();
    context.read<AuthService>().logout();
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();
    final ws = context.watch<WebSocketService>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('NexusAPI — Task Board'),
        actions: [
          // WebSocket connection indicator
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Chip(
              avatar: CircleAvatar(
                radius: 6,
                backgroundColor:
                    ws.isConnected ? Colors.green : Colors.orange,
              ),
              label: Text(ws.isConnected ? 'Live' : 'Offline'),
              visualDensity: VisualDensity.compact,
            ),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Sign out (${auth.currentUser?.username})',
            onPressed: _logout,
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final result = await showDialog<(String, String?)>(
            context: context,
            builder: (_) => const CreateTaskDialog(),
          );
          if (result != null) await _createTask(result.$1, result.$2);
        },
        icon: const Icon(Icons.add),
        label: const Text('New Task'),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!, style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 16),
            FilledButton(onPressed: _loadTasks, child: const Text('Retry')),
          ],
        ),
      );
    }
    // Kanban board — horizontal scroll with one column per status
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: TaskStatus.values.map((status) => _KanbanColumn(
          status: status,
          tasks: _tasks.where((t) => t.status == status).toList(),
          onStatusChanged: _updateTaskStatus,
        )).toList(),
      ),
    );
  }
}

/// A single Kanban column for one [TaskStatus].
class _KanbanColumn extends StatelessWidget {
  final TaskStatus status;
  final List<Task> tasks;
  final Future<void> Function(Task, TaskStatus) onStatusChanged;

  const _KanbanColumn({
    required this.status,
    required this.tasks,
    required this.onStatusChanged,
  });

  static const _statusColors = {
    TaskStatus.todo:       Color(0xFF6B7280),
    TaskStatus.inProgress: Color(0xFF3B82F6),
    TaskStatus.inReview:   Color(0xFFF59E0B),
    TaskStatus.done:       Color(0xFF10B981),
  };

  @override
  Widget build(BuildContext context) {
    final color = _statusColors[status]!;

    return Container(
      width: 280,
      margin: const EdgeInsets.only(right: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Column header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: color.withOpacity(0.3)),
            ),
            child: Row(
              children: [
                CircleAvatar(radius: 6, backgroundColor: color),
                const SizedBox(width: 8),
                Text(
                  status.label,
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: color,
                  ),
                ),
                const Spacer(),
                Text(
                  '${tasks.length}',
                  style: TextStyle(color: color.withOpacity(0.8), fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          // Task cards
          ...tasks.map((task) => TaskCard(
            task: task,
            onStatusChanged: (newStatus) => onStatusChanged(task, newStatus),
          )),
          if (tasks.isEmpty)
            Container(
              height: 80,
              decoration: BoxDecoration(
                border: Border.all(
                  color: Colors.grey.withOpacity(0.2),
                  style: BorderStyle.solid,
                ),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Center(
                child: Text(
                  'No tasks',
                  style: TextStyle(color: Colors.grey.withOpacity(0.5)),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
