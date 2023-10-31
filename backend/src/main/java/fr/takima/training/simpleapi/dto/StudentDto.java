package fr.takima.training.simpleapi.dto;

// Records are perfoect for DTOs
public record StudentDto(
        String firstname,
        String lastname,
        Long departmentId) {
}
