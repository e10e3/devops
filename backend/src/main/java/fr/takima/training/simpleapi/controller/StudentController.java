package fr.takima.training.simpleapi.controller;

import fr.takima.training.simpleapi.dto.StudentDto;
import fr.takima.training.simpleapi.entity.Student;
import fr.takima.training.simpleapi.service.DepartmentService;
import fr.takima.training.simpleapi.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping(value = "/students")
public class StudentController {
    private final StudentService studentService;
    private final DepartmentService departmentService;

    private final Student studentDtoToStudent(StudentDto studentDto) {
        Student student = new Student();
        student.setId(0L); // A DTO has no ID
        student.setFirstname(studentDto.firstname());
        student.setLastname(studentDto.lastname());
        student.setDepartment(
                departmentService.getDepartmentById(studentDto.departmentId()));
        return student;
    }

    @Autowired
    public StudentController(StudentService studentService,
            DepartmentService departmentService) {
        this.studentService = studentService;
        this.departmentService = departmentService;
    }

    @GetMapping(value = "/")
    public ResponseEntity<Object> getStudents() {
        return ResponseEntity.ok(studentService.getAll());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<Object> getStudentById(@PathVariable(name = "id") long id) {
        Optional<Student> studentOptional = Optional.ofNullable(this.studentService.getStudentById(id));
        if (studentOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(studentOptional.get());
    }

    @PostMapping
    public ResponseEntity<Object> addStudent(@RequestBody StudentDto studentDto) {
        Student savedStudent;
        try {
            savedStudent = this.studentService.addStudent(
                    this.studentDtoToStudent(studentDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(savedStudent.getId())
                .toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<Object> updateStudent(@RequestBody StudentDto studentDto,
            @PathVariable(name = "id") long id) {
        Optional<Student> studentOptional = Optional.ofNullable(studentService.getStudentById(id));
        if (studentOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Student student = this.studentDtoToStudent(studentDto);
        student.setId(id);
        this.studentService.addStudent(student);
        return ResponseEntity.ok(student);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> removeStudent(@PathVariable(name = "id") long id) {
        Optional<Student> studentOptional = Optional.ofNullable(studentService.getStudentById(id));
        if (studentOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        this.studentService.removeStudentById(id);

        return ResponseEntity.ok().build();
    }
}
