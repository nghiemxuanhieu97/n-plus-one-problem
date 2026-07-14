package com.example.joinfetch;

import com.example.joinfetch.aspect.CollectingQueryListener;
import com.example.joinfetch.dto.AuthorBooksDto;
import com.example.joinfetch.dto.record.Response;
import com.example.joinfetch.service.AuthorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:joinfetch-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "demo.large-dataset=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureMockMvc
class AuthorBenchmarkResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void joinFetchBooksResponseContainsBooksOnly() throws Exception {
        mockMvc.perform(get("/demo/join-fetch/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("JOIN_FETCH_BOOKS"))
                .andExpect(jsonPath("$.result[0].id").exists())
                .andExpect(jsonPath("$.result[0].name").exists())
                .andExpect(jsonPath("$.result[0].books").isArray())
                .andExpect(jsonPath("$.result[0].books[0].id").exists())
                .andExpect(jsonPath("$.result[0].books[0].title").exists())
                .andExpect(jsonPath("$.result[0].books[0].publishYear").exists())
                .andExpect(jsonPath("$.result[0].books[0].author").doesNotExist())
                .andExpect(jsonPath("$.result[0].books[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.result[0].country").doesNotExist())
                .andExpect(jsonPath("$.result[0].awards").doesNotExist());
    }

    @Test
    void joinFetchCountryResponseContainsCountryOnly() throws Exception {
        mockMvc.perform(get("/demo/join-fetch/to-one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("JOIN_FETCH_TO_ONE"))
                .andExpect(jsonPath("$.result[0].id").exists())
                .andExpect(jsonPath("$.result[0].name").exists())
                .andExpect(jsonPath("$.result[0].country.id").exists())
                .andExpect(jsonPath("$.result[0].country.name").exists())
                .andExpect(jsonPath("$.result[0].country.authors").doesNotExist())
                .andExpect(jsonPath("$.result[0].books").doesNotExist())
                .andExpect(jsonPath("$.result[0].awards").doesNotExist());
    }

    @Test
    void cartesianResponseContainsBooksAndAwardsButNoCountry() throws Exception {
        mockMvc.perform(get("/demo/join-fetch/cartesian"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("JOIN_FETCH_CARTESIAN"))
                .andExpect(jsonPath("$.result[0].books").isArray())
                .andExpect(jsonPath("$.result[0].awards").isArray())
                .andExpect(jsonPath("$.result[0].books[0].author").doesNotExist())
                .andExpect(jsonPath("$.result[0].awards[0].author").doesNotExist())
                .andExpect(jsonPath("$.result[0].country").doesNotExist());
    }

    @Test
    void authorBasicResponseContainsNoAssociations() throws Exception {
        mockMvc.perform(get("/demo/authors/basic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("AUTHORS_ONLY"))
                .andExpect(jsonPath("$.result[0].id").exists())
                .andExpect(jsonPath("$.result[0].name").exists())
                .andExpect(jsonPath("$.result[0].country").doesNotExist())
                .andExpect(jsonPath("$.result[0].books").doesNotExist())
                .andExpect(jsonPath("$.result[0].awards").doesNotExist());
    }

    @Test
    void dtoSerializationDoesNotTriggerSqlAfterServiceReturns() {
        Response<List<AuthorBooksDto>> response = authorService.demoJoinFetchBooks();
        CollectingQueryListener.getAndClear();

        assertThatCode(() -> objectMapper.writeValueAsString(response))
                .doesNotThrowAnyException();
        assertThat(CollectingQueryListener.getAndClear()).isEmpty();
    }

    @Test
    void jsonHasNoAuthorBookAuthorLoop() throws Exception {
        Response<List<AuthorBooksDto>> response = authorService.demoJoinFetchBooks();
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("authorId");
        assertThat(json).doesNotContain("\"author\"");
    }

    @Test
    void nPlusOneBooksStillExecutesAdditionalSqlStatements() {
        Response<List<AuthorBooksDto>> response = authorService.demoNPlusOneBook();

        assertThat(response.sqlStatementCount()).isGreaterThan(1);
        assertThat(response.result()).isNotEmpty();
        assertThat(response.result().get(0).books()).isNotEmpty();
    }

    @Test
    void metricsAndSqlCollectionAreReturned() {
        Response<List<AuthorBooksDto>> response = authorService.demoJoinFetchBooks();

        assertThat(response.ormQueryExecutionCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.ormQueries()).isNotEmpty();
        assertThat(response.sqlStatementCount()).isEqualTo(response.sqlStatements().size());
        assertThat(response.sqlStatementCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.preparedStatementCount()).isGreaterThanOrEqualTo(response.sqlStatementCount());
        assertThat(response.estimatedDatabaseRows()).isGreaterThan(0);
    }
}
