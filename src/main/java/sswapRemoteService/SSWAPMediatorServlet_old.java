package sswapRemoteService;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RiotException;
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

@WebServlet("/convert_old")
public class SSWAPMediatorServlet_old extends HttpServlet {

    // Namespaces used in RDG and RIG
    private static final String BASE_URI = "http://example.com/cottage#";
    private static final String SERVICE_NS = "http://example.com/CottageBookingService#";
    private static final String RDG_RESOURCE = "ontology/booking-service-rdg.ttl";

    // sswap namespace (used in RDG)
    private static final String SSNAP = "http://sswapmeet.sswap.info/sswap#";

    // cached RDG-derived lists
    private List<Property> requiredInputProperties;
    private List<Property> outputProperties;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            Model rdg = loadRdgModel();
            requiredInputProperties = extractInputProperties(rdg);
            outputProperties = extractOutputProperties(rdg);
            if (requiredInputProperties == null) requiredInputProperties = new ArrayList<>();
            if (outputProperties == null) outputProperties = new ArrayList<>();
        } catch (IOException e) {
            throw new ServletException("Failed to load RDG: " + e.getMessage(), e);
        }
    }

    // POST receives RIG (RDF/XML)
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Read request body completely
        String inputXml;
        try (InputStream in = request.getInputStream()) {
            inputXml = streamToString(in);
        }

        // Parse incoming RIG as RDF/XML
        Model rigModel = ModelFactory.createDefaultModel();
        try (InputStream bais = new ByteArrayInputStream(inputXml.getBytes(StandardCharsets.UTF_8))) {
            // Explicitly use RDF/XML
            rigModel.read(bais, null, "TURTLE");
        } catch (RiotException re) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid RIG TURTLE: " + re.getMessage());
            return;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error parsing RIG: " + e.getMessage());
            return;
        }

        // Validate structure against RDG
        Resource requestSubject = rigModel.getResource(BASE_URI +"CottageBookingService#" + "RequestQueryCottage");
        if (!rigModel.containsResource(requestSubject)) {
            // resource may be a typed blank node instead of named resource, check types:
            StmtIterator typeStmts = rigModel.listStatements(null, RDF.type, rigModel.createResource(BASE_URI + "BookingRequest"));
            if (!typeStmts.hasNext()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("RIG missing required subject: <" + BASE_URI +"CottageBookingService#" + "RequestQueryCottage>");
                return;
            } else {
                // If typed node exists, use that subject (first match)
                requestSubject = typeStmts.nextStatement().getSubject();
            }
        }

        // Check all required properties present
        List<String> missing = new ArrayList<>();
        for (Property p : requiredInputProperties) {
            if (!rigModel.contains(requestSubject, p)) {
                missing.add(p.getLocalName());
            }
        }
        if (!missing.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("RIG missing required properties: " + String.join(", ", missing));
            return;
        }

        // Extract safe values (use literal string when possible)
        // We'll use common names from RDG (local names)
        Map<String, String> inputs = new HashMap<>();
        for (Property p : requiredInputProperties) {
            String local = p.getLocalName();
            String val = safeGetLiteralString(rigModel, requestSubject, p, "");
            inputs.put(local, val);
        }

        // Example: parse and validate numeric/date values (basic)
        int numOfPeople = parseIntSafe(inputs.get("numOfPeople"), 0);
        int numOfBedrooms = parseIntSafe(inputs.get("numOfBedrooms"), 0);
        int reqNumOfDays = parseIntSafe(inputs.get("reqNumOfDays"), 0);
        String bookingDateStr = inputs.get("bookingDate");
        LocalDate bookingDate;
        try {
            bookingDate = LocalDate.parse(bookingDateStr, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid bookingDate format. Expected YYYY-MM-DD. Value: " + bookingDateStr);
            return;
        }

        // Business logic: in a real service you'd compute available cottages; here we synthesize an RRG.
        LocalDate endDate = bookingDate.plusDays(reqNumOfDays);

        // Build RRG model programmatically, using RDG mapsTo shape (outputProperties)
        Model rrg = ModelFactory.createDefaultModel();

        // create response graph resource
        Resource responseGraph = rrg.createResource(SERVICE_NS + "ResponseQueryCottage");
        responseGraph.addProperty(RDF.type, rrg.createResource(SSNAP + "ResponseGraph"));

        // create the BookingResponse object (anonymous resource is fine)
        Resource bookingResponse = rrg.createResource();
        bookingResponse.addProperty(RDF.type, rrg.createResource(BASE_URI + "BookingResponse"));

        // Populate output properties with reasonable values (some values taken from inputs)
        // We'll iterate through outputProperties and set values by local name (common-sense mapping).
        for (Property op : outputProperties) {
            String key = op.getLocalName();
            switch (key) {
                case "cottageImageUrl":
                    bookingResponse.addLiteral(op, "https://example.org/images/cottage.jpg");
                    break;
                case "cottageRealCapacity":
                    bookingResponse.addLiteral(op, Integer.toString(Math.max(2, numOfPeople)));
                    break;
                case "numOfBedrooms":
                    bookingResponse.addLiteral(op, Integer.toString(Math.max(1, numOfBedrooms)));
                    break;
                case "distanceToLake":
                    bookingResponse.addLiteral(op, inputs.getOrDefault("distanceToLake", "0.0"));
                    break;
                case "closestCity":
                    bookingResponse.addLiteral(op, inputs.getOrDefault("closestCity", ""));
                    break;
                case "distanceToClosestCity":
                    bookingResponse.addLiteral(op, inputs.getOrDefault("distanceToClosestCity", "0.0"));
                    break;
                case "bookingNumber":
                    bookingResponse.addLiteral(op, "BKG-" + new Random().nextInt(10000));
                    break;
                case "startOfBooking":
                    bookingResponse.addLiteral(op, bookingDate.toString());
                    break;
                case "endOfBooking":
                    bookingResponse.addLiteral(op, endDate.toString());
                    break;
                default:
                    // fallback: echo empty or copy input if exists
                    if (inputs.containsKey(key)) {
                        bookingResponse.addLiteral(op, inputs.get(key));
                    } else {
                        bookingResponse.addLiteral(op, "");
                    }
            }
        }

        // link responseGraph -> bookingResponse using service-specific predicate (using SERVICE_NS:hasMapping per earlier)
        Property hasMapping = rrg.createProperty(SERVICE_NS, "hasMapping");
        responseGraph.addProperty(hasMapping, bookingResponse);

        // Write RRG as RDF/XML to response
        response.setContentType("text/turtle");
        try (OutputStream out = response.getOutputStream()) {
            rrg.write(out, "TURTLE");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Failed to write RRG: " + e.getMessage());
        }
    }

    // ---------- Helper methods ----------

    private Model loadRdgModel() throws IOException {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RDG_RESOURCE)) {
            if (in == null) throw new FileNotFoundException("RDG resource not found: " + RDG_RESOURCE);
            // RDG is Turtle
            model.read(in, null, "TURTLE");
        }
        return model;
    }

    private List<Property> extractInputProperties(Model rdg) {
        List<Property> result = new ArrayList<>();

        // Find the operatesOn -> hasMapping -> subject node that contains c:BookingRequest
        // We'll search for a blank node that has rdf:type of sswap:Subject or c:BookingRequest
        Resource sswapSubjectType = rdg.createResource(SSNAP + "Subject");
        Resource bookingRequest = rdg.createResource(BASE_URI + "BookingRequest");

        // Search for the mapping blank node which has predicate rdf:type sswap:Subject and has properties in c: namespace
        StmtIterator it = rdg.listStatements(null, rdg.createProperty(SSNAP + "hasMapping"), (RDFNode) null);
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            RDFNode mappingNode = stmt.getObject(); // this should be a blank node that contains rdf:type sswap:Subject

            if (mappingNode != null && mappingNode.isResource()) {
                Resource mapping = mappingNode.asResource();
                // find a child that is rdf:type sswap:Subject (or the mapping itself may be the blank node containing triple patterns)
                // The RDG uses nested constructs: operatesOn [ sswap:hasMapping [ rdf:type sswap:Subject , c:BookingRequest ; c:prop "" ; ... ] ... ]
                // So we search statements where subject==mapping and predicate is rdf:type with object bookingRequest or sswapSubjectType
                StmtIterator types = rdg.listStatements(mapping, RDF.type, (RDFNode) null);
                boolean looksLikeSubject = false;
                while (types.hasNext()) {
                    RDFNode obj = types.nextStatement().getObject();
                    if (obj.isResource()) {
                        String uri = obj.asResource().getURI();
                        if ( (SSNAP + "Subject").equals(uri)
                                || (BASE_URI + "BookingRequest").equals(uri) ) {
                            looksLikeSubject = true;
                            break;
                        }
                    }
                }

                // If not found directly on mapping node, sometimes mapping is nested deeper; also check mapping's properties that are in c: namespace
                if (!looksLikeSubject) {
                    // check properties on mapping that are in BASE_URI
                    StmtIterator members = rdg.listStatements(mapping, (Property) null, (RDFNode) null);
                    while (members.hasNext()) {
                        Statement s = members.nextStatement();
                        Property p = s.getPredicate();
                        if (p.getURI() != null && p.getURI().startsWith(BASE_URI)) {
                            looksLikeSubject = true;
                            break;
                        }
                    }
                }

                if (looksLikeSubject) {
                    // collect properties under this mapping node that are in BASE_URI and have empty literal object in RDG
                    StmtIterator props = rdg.listStatements(mapping, (Property) null, (RDFNode) null);
                    while (props.hasNext()) {
                        Statement s = props.nextStatement();
                        Property pred = s.getPredicate();
                        if (pred.getURI() != null && pred.getURI().startsWith(BASE_URI)) {
                            result.add(rdg.createProperty(pred.getURI()));
                        }
                    }
                }
            }
        }

        // If not found via hasMapping traversal, fallback: scan RDG for triple where subject is a resource of type BookingRequest and collect predicates
        if (result.isEmpty()) {
            StmtIterator possibleSubjects = rdg.listStatements();
            while (possibleSubjects.hasNext()) {
                Statement s = possibleSubjects.nextStatement();
                if (s.getObject().isResource() && s.getObject().asResource().getURI() != null &&
                        s.getObject().asResource().getURI().equals(BASE_URI + "BookingRequest")) {
                    // collect predicates used with that subject in RDG
                    Resource subj = s.getSubject();
                    StmtIterator ps = rdg.listStatements(subj, (Property) null, (RDFNode) null);
                    while (ps.hasNext()) {
                        Statement psStmt = ps.nextStatement();
                        if (psStmt.getPredicate().getURI().startsWith(BASE_URI)) {
                            result.add(rdg.createProperty(psStmt.getPredicate().getURI()));
                        }
                    }
                    break;
                }
            }
        }

        // In case duplicates, unique them preserving order:
        List<Property> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Property p : result) {
            if (!seen.contains(p.getURI())) {
                unique.add(p);
                seen.add(p.getURI());
            }
        }
        return unique;
    }

    private List<Property> extractOutputProperties(Model rdg) {
        List<Property> result = new ArrayList<>();

        // find sswap:mapsTo -> object node which contains c:BookingResponse properties
        StmtIterator it = rdg.listStatements(null, rdg.createProperty(SSNAP + "operatesOn"), (RDFNode) null);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            Resource operatesOn = st.getObject().asResource();

            // inside operatesOn there is sswap:mapsTo ...
            StmtIterator maps = rdg.listStatements(operatesOn, rdg.createProperty(SSNAP + "mapsTo"), (RDFNode) null);
            while (maps.hasNext()) {
                RDFNode mapsNode = maps.nextStatement().getObject();
                if (mapsNode != null && mapsNode.isResource()) {
                    Resource mapsRes = mapsNode.asResource();
                    // collect its base-uri properties
                    StmtIterator props = rdg.listStatements(mapsRes, (Property) null, (RDFNode) null);
                    while (props.hasNext()) {
                        Statement s = props.nextStatement();
                        Property pred = s.getPredicate();
                        if (pred.getURI() != null && pred.getURI().startsWith(BASE_URI)) {
                            result.add(rdg.createProperty(pred.getURI()));
                        }
                    }
                }
            }
        }

        // dedupe and return
        List<Property> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Property p : result) {
            if (!seen.contains(p.getURI())) {
                unique.add(p);
                seen.add(p.getURI());
            }
        }
        return unique;
    }

    private static String streamToString(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private String safeGetLiteralString(Model m, Resource subj, Property prop, String defaultVal) {
        StmtIterator it = m.listStatements(subj, prop, (RDFNode) null);
        if (!it.hasNext()) return defaultVal;
        RDFNode node = it.nextStatement().getObject();
        if (node.isLiteral()) {
            return node.asLiteral().getString();
        } else {
            return node.toString();
        }
    }

    private int parseIntSafe(String s, int fallback) {
        if (s == null) return fallback;
        try {
            // strip possible datatype lexical forms
            if (s.contains("^^")) s = s.substring(0, s.indexOf("^^"));
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
