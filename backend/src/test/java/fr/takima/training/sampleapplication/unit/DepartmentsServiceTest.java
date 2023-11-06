package fr.takima.training.sampleapplication.unit;

import fr.takima.training.simpleapi.dao.DepartmentDAO;
import fr.takima.training.simpleapi.entity.Department;
import fr.takima.training.simpleapi.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class DepartmentsServiceTest {

    @InjectMocks
    private DepartmentService departmentService;

    @Mock
    private DepartmentDAO departmentDAO;

    private final Department DEPARTMENT = Department.builder()
            .id(1L)
            .name("DepartementTest")
            .build();

    @Test
    void testGetDepartmentByName() {
        when(departmentDAO.findDepartmentByName("DepartmentTest")).thenReturn(DEPARTMENT);
        assertEquals(DEPARTMENT, departmentService.getDepartmentByName("DepartmentTest"));
    }

    @Test
    void testGetDepartmentByNameWithNullValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentByName(null));
    }

    @Test
    void testGetDepartmentByNameWithEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentByName(""));
    }

    @Test
    void testGetDepartmentById() {
        when(departmentDAO.findById(1L)).thenReturn(Optional.of(DEPARTMENT));
        assertEquals(DEPARTMENT, departmentService.getDepartmentById(1L));
    }

    @Test
    void testGetDepartmentByUnknownId() {
        when(departmentDAO.findById(100L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> departmentService.getDepartmentById(100L));
    }

    @Test
    void testGetDepartmentByIdWithNullValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentById(null));
    }

    @Test
    void testGetDepartmentByIdWithNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentById(-1L));
    }

    @Test
    void testGetAllDepartments() {
        when(departmentDAO.findAll()).thenReturn(List.of(DEPARTMENT));
        assertEquals(1, departmentService.getDepartments().size());
        assertEquals(DEPARTMENT, departmentService.getDepartments().get(0));
    }
}
