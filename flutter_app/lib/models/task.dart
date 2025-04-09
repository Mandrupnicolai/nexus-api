class Task {
  final String id;
  final String title;
  final String? description;
  final String status;
  final int priority;
  final String projectId;
  final String? assigneeId;
  final DateTime createdAt;
  final DateTime updatedAt;

  Task({
    required this.id,
    required this.title,
    this.description,
    required this.status,
    required this.priority,
    required this.projectId,
    this.assigneeId,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Task.fromJson(Map<String, dynamic> json) => Task(
        id: json['id'],
        title: json['title'],
        description: json['description'],
        status: json['status'],
        priority: json['priority'] ?? 0,
        projectId: json['projectId'],
        assigneeId: json['assignee']?['id'],
        createdAt: DateTime.parse(json['createdAt']),
        updatedAt: DateTime.parse(json['updatedAt']),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'description': description,
        'status': status,
        'priority': priority,
        'projectId': projectId,
      };

  Task copyWith({String? status}) => Task(
        id: id,
        title: title,
        description: description,
        status: status ?? this.status,
        priority: priority,
        projectId: projectId,
        assigneeId: assigneeId,
        createdAt: createdAt,
        updatedAt: updatedAt,
      );
}