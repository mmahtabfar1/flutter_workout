/// The features a workout reading can have
enum WorkoutFeature {
  /// An unknown workout feature
  unknown,

  /// Heart rate
  heartRate,

  /// Calories burned
  calories,

  /// Steps taken
  steps,

  /// Distance traveled in meters
  distance,

  /// Speed in m/s
  speed,

  /// GPS location as String of "${Latitude}/${Longitude}"
  location,

  /// Power in Watts
  power,

  /// Cadence in RPM
  cadence,
}
