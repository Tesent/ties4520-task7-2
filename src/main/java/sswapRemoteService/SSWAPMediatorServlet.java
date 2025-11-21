package sswapRemoteService;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.vocabulary.RDF;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@WebServlet("/mediator")
public class SSWAPMediatorServlet extends HttpServlet {

    private static final String BASE_URI = "http://example.com/cottage#";
    private static final String SERVICE_NS = "http://example.com/CottageBookingService#";
    private static final String SSWAP_NS = "http://sswapmeet.sswap.info/sswap#";


    private Model cottageModel;
    private Model rdgModel;

    @Override
    public void init() throws ServletException {
        rdgModel = loadTTL("ontology/booking-service-rdg.ttl");
        cottageModel = loadCottageModel();
    }

    private Model loadTTL(String path) throws ServletException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new ServletException("File not found: " + path);
            Model model = ModelFactory.createDefaultModel();
            model.read(in, null, "TTL");
            return model;
        } catch (IOException e) {
            throw new ServletException("Failed to load TTL file: " + path, e);
        }
    }

    private Model loadCottageModel() throws ServletException {
        try (InputStream indivIn = getClass().getClassLoader().getResourceAsStream("ontology/v2-individuals.ttl");
             InputStream bookIn = getClass().getClassLoader().getResourceAsStream("ontology/v2-cottage-booking.owl")) {

            if (indivIn == null || bookIn == null) {
                throw new ServletException("Missing TTL/OWL files");
            }

            Model model = ModelFactory.createDefaultModel();
            model.read(indivIn, null, "TTL");
            model.read(bookIn, null, "TTL");
            return model;

        } catch (IOException e) {
            throw new ServletException("Failed to read cottage TTL/OWL files", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Parse RIG
        Model rigModel = ModelFactory.createDefaultModel();
        try (InputStream in = request.getInputStream()) {
            rigModel.read(in, null, "TTL");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid RIG TTL: " + e.getMessage());
            return;
        }

        // Get request subject
        Resource requestSubject = rigModel.getResource(SERVICE_NS + "RequestQueryCottage");
        Property hasMapping = rigModel.createProperty(SSWAP_NS, "hasMapping");

        StmtIterator mappingIter = rigModel.listStatements(requestSubject, hasMapping, (RDFNode) null);
        if (!mappingIter.hasNext()) {
            throw new IllegalStateException("RIG has no mapping");
        }

        RDFNode mappingNode = mappingIter.nextStatement().getObject();
        Resource rig_mapping = mappingNode.asResource(); // This is the blank node
        // Extract input values
        double distanceToLake = getDouble(rig_mapping, "distanceToLake");
        double distanceToCity = getDouble(rig_mapping, "distanceToClosestCity");
        int numOfPeople = getInt(rig_mapping, "numOfPeople");
        int numOfBedrooms = getInt(rig_mapping, "numOfBedrooms");
        String closestCity = getString(rig_mapping, "closestCity");
        int reqNumOfDays = getInt(rig_mapping, "reqNumOfDays");
        LocalDate bookingDate = getDate(rig_mapping, "bookingDate");

        LocalDate candidateStart = bookingDate;
        LocalDate candidateEnd = candidateStart.plusDays(reqNumOfDays);

        // SPARQL query directly using RIG input
        //ParameterizedSparqlString ss = new ParameterizedSparqlString(
        //        "PREFIX c: <http://example.com/cottage#> " +
        //                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
        //                "SELECT ?bookingNumber ?capacity ?bedrooms ?distToLake ?cloCity ?distToCloCity ?bookAdd ?cImgUrl " +
        //                "?startOfBooking ?endOfBooking WHERE { " +
        //                "?booking a c:Booking; " +
        //                "   c:bookingNumber ?bookingNumber; " +
        //                "   c:startOfBooking ?startOfBooking; " +
        //                "   c:endOfBooking ?endOfBooking; " +
        //                "   c:hasCottage ?cottage. " +
        //                "?cottage a c:Cottage; " +
        //                "   c:cottageRealCapacity ?capacity; " +
        //                "   c:numOfBedrooms ?bedrooms; " +
        //                "   c:distanceToLake ?distToLake; " +
        //                "   c:closestCity ?cloCity; " +
        //                "   c:distanceToClosestCity ?distToCloCity; " +
        //                "   c:bookingAddress ?bookAdd; " +
        //                "   c:cottageImageUrl ?cImgUrl. " +
        //                "FILTER(?distToLake <= ?maxDistanceToLake) " +
        //                "FILTER(?distToCloCity <= ?maxDistanceToCloCity) " +
        //                "FILTER(?bedrooms >= ?numOfBedrooms) " +
        //                "FILTER(?capacity >= ?numOfPeople) " +
        //                (closestCity != null && !closestCity.isEmpty() ? "FILTER(?cloCity = ?closestCity) " : "") +
        //                "FILTER NOT EXISTS { " +
        //                "?conflictBooking a c:Booking; " +
        //                "   c:startOfBooking ?startOfBooking; " +
        //                "   c:endOfBooking ?endOfBooking; " +
        //                "   c:hasCottage ?cottage. " +
        //                "FILTER (?startOfBooking <= ?candidateEndLiteral && ?endOfBooking >= ?candidateStartLiteral) " +
        //                "} }"
        //);

        // Build base SPARQL query
        StringBuilder q = new StringBuilder("PREFIX c: <http://example.com/cottage#> " +
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
                "SELECT ?bookingNumber ?capacity ?bedrooms ?distToLake ?cloCity ?distToCloCity ?bookAdd ?cImgUrl ?startOfBooking ?endOfBooking " +
                "WHERE { " +
                "  ?booking a c:Booking; " +
                "           c:bookingNumber ?bookingNumber; " +
                "           c:startOfBooking ?startOfBooking; " +
                "           c:endOfBooking ?endOfBooking; " +
                "           c:hasCottage ?cottage . " +
                "  ?cottage a c:Cottage; " +
                "           c:cottageRealCapacity ?capacity; " +
                "           c:numOfBedrooms ?bedrooms; " +
                "           c:distanceToLake ?distToLake; " +
                "           c:closestCity ?cloCity; " +
                "           c:distanceToClosestCity ?distToCloCity; " +
                "           c:bookingAddress ?bookAdd; " +
                "           c:cottageImageUrl ?cImgUrl . ");

        // Conditional filters

        if (distanceToLake > 0) q.append("FILTER(?distToLake <= ?maxDistanceToLake) ");
        if (distanceToCity > 0) q.append("FILTER(?distToCloCity <= ?maxDistanceToCloCity) ");
        if (numOfBedrooms > 0) q.append("FILTER(?bedrooms >= ?numOfBedrooms) ");
        if (numOfPeople > 0) q.append("FILTER(?capacity >= ?numOfPeople) ");
        if (closestCity != null && !closestCity.isEmpty()) q.append("FILTER(?cloCity = ?closestCity) ");

        q.append("FILTER NOT EXISTS { " +
                "  ?conflictBooking a c:Booking ; " +
                "                   c:startOfBooking ?confStart ; " +
                "                   c:endOfBooking ?confEnd ; " +
                "                   c:hasCottage ?confCottage . " +
                "  FILTER (?confStart <= ?candidateEndLiteral && ?confEnd >= ?candidateStartLiteral) " +
                "} ");


        q.append("}");


        ParameterizedSparqlString ss = new ParameterizedSparqlString(q.toString());
        // Bind parameters
        ss.setLiteral("maxDistanceToLake", ResourceFactory.createTypedLiteral(distanceToLake));
        ss.setLiteral("maxDistanceToCloCity", ResourceFactory.createTypedLiteral(distanceToCity));
        ss.setLiteral("numOfBedrooms", ResourceFactory.createTypedLiteral(numOfBedrooms));
        ss.setLiteral("numOfPeople", ResourceFactory.createTypedLiteral(numOfPeople));
        if (closestCity != null && !closestCity.isEmpty()) ss.setLiteral("closestCity", closestCity);
        ss.setLiteral("candidateStartLiteral", ResourceFactory.createTypedLiteral(candidateStart.toString(), XSDDatatype.XSDdate));
        ss.setLiteral("candidateEndLiteral", ResourceFactory.createTypedLiteral(candidateEnd.toString(), XSDDatatype.XSDdate));

        StringBuilder rrgTtl = new StringBuilder();

// Add prefixes
        rrgTtl.append("@prefix c: <http://example.com/cottage#> .\n")
                .append("@prefix service: <").append(SERVICE_NS).append("> .\n")
                .append("@prefix sswap: <http://sswapmeet.sswap.info/sswap#> .\n")
                .append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n")
                .append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n\n");

// Start Response Graph
        rrgTtl.append("service:ResponseQueryCottage\n")
                .append("    rdf:type sswap:ResponseGraph ;\n");

// Execute the SPARQL query
        Query query = QueryFactory.create(ss.asQuery());
        try (QueryExecution qe = QueryExecutionFactory.create(query, cottageModel)) {
            ResultSet results = qe.execSelect();

            boolean first = true;
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();

                if (!first) {
                    rrgTtl.append("    ;\n"); // separate multiple hasMapping entries
                } else {
                    first = false;
                }

                rrgTtl.append("    sswap:hasMapping [\n")
                        .append("        rdf:type sswap:Object , c:BookingResponse ;\n")
                        .append("        c:cottageImageUrl <").append(
                                soln.get("cImgUrl").isResource() ?
                                        soln.get("cImgUrl").asResource().getURI() :
                                        soln.get("cImgUrl").asLiteral().getString()
                        ).append("> ;\n")
                        .append("        c:cottageRealCapacity ").append(soln.getLiteral("capacity").getInt()).append(" ;\n")
                        .append("        c:numOfBedrooms ").append(soln.getLiteral("bedrooms").getInt()).append(" ;\n")
                        .append("        c:distanceToLake ").append(soln.getLiteral("distToLake").getDouble()).append(" ;\n")
                        .append("        c:distanceToClosestCity ").append(soln.getLiteral("distToCloCity").getDouble()).append(" ;\n")
                        .append("        c:closestCity \"").append(soln.getLiteral("cloCity").getString()).append("\" ;\n")
                        .append("        c:bookingNumber ").append(soln.getLiteral("bookingNumber").getInt()).append(" ;\n")
                        .append("        c:startOfBooking \"").append(soln.getLiteral("startOfBooking").getString())
                        .append("\"^^xsd:date ;\n")
                        .append("        c:endOfBooking \"").append(soln.getLiteral("endOfBooking").getString())
                        .append("\"^^xsd:date\n")
                        .append("    ]");
            }

            rrgTtl.append(" .\n"); // close the ResponseGraph
        }

// Return TTL
        response.setContentType("text/turtle");
        try (OutputStream out = response.getOutputStream()) {
            out.write(rrgTtl.toString().getBytes(StandardCharsets.UTF_8));
        }

    }

    // ===== Helper methods =====
    private int getInt(Resource res, String prop) {
        Statement stmt = res.getProperty(res.getModel().createProperty(BASE_URI, prop));
        if (stmt != null && stmt.getObject().isLiteral()) return stmt.getObject().asLiteral().getInt();
        return 0;
    }

    private double getDouble(Resource res, String prop) {
        Statement stmt = res.getProperty(res.getModel().createProperty(BASE_URI, prop));
        if (stmt != null && stmt.getObject().isLiteral()) return stmt.getObject().asLiteral().getDouble();
        return 0.0;
    }

    private String getString(Resource res, String prop) {
        Statement stmt = res.getProperty(res.getModel().createProperty(BASE_URI, prop));
        if (stmt != null && stmt.getObject().isLiteral()) return stmt.getObject().asLiteral().getString();
        return "";
    }

    private LocalDate getDate(Resource res, String prop) {
        Statement stmt = res.getProperty(res.getModel().createProperty(BASE_URI, prop));
        if (stmt != null && stmt.getObject().isLiteral())
            return LocalDate.parse(stmt.getObject().asLiteral().getString(), DateTimeFormatter.ISO_DATE);
        return LocalDate.now();
    }
}
