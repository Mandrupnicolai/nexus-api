class AuthResponse {
  final String accessToken;
  final String tokenType;
  final int expiresIn;
  final UserProfile user;

  AuthResponse({
    required this.accessToken,
    required this.tokenType,
    required this.expiresIn,
    required this.user,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['accessToken'],
        tokenType: json['tokenType'] ?? 'Bearer',
        expiresIn: json['expiresIn'],
        user: UserProfile.fromJson(json['user']),
      );
}

class UserProfile {
  final String id;
  final String displayName;
  final String email;
  final String? avatarUrl;

  UserProfile({
    required this.id,
    required this.displayName,
    required this.email,
    this.avatarUrl,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: json['id'],
        displayName: json['displayName'],
        email: json['email'],
        avatarUrl: json['avatarUrl'],
      );
}