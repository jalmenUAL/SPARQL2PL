package SPARQL2PL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.FileUtils;
import org.jpl7.Atom;
import org.jpl7.Term;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.server.VaadinSession;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.enums.AceTheme;

/**
 * A sample Vaadin view class.
 * <p>
 * To implement a Vaadin view just extend any Vaadin component and use @Route
 * annotation to announce it in a URL as a Spring managed bean. Use the @PWA
 * annotation make the application installable on phones, tablets and some
 * desktop browsers.
 * <p>
 * A new instance of this class is created for every new user and every browser
 * tab/window.
 */

@Route
@PWA(name = "Vaadin Application", shortName = "Vaadin App", description = "This is an example Vaadin application.", enableInstallPrompt = false)
@CssImport("./styles/shared-styles.css")
@CssImport(value = "./styles/vaadin-text-field-styles.css", themeFor = "vaadin-text-field")
public class MainView extends VerticalLayout {

	TextField file = new TextField();
	Integer step = 0;

	VerticalLayout ldataset_fuzzy = new VerticalLayout();
	VerticalLayout ldataset_crisp = new VerticalLayout();
	Grid<HashMap<String, RDFNode>> dataset_fuzzy = new Grid<HashMap<String, RDFNode>>();
	Grid<HashMap<String, RDFNode>> dataset_crisp = new Grid<HashMap<String, RDFNode>>();
	OntModel model = ModelFactory.createOntologyModel();
	AceEditor editor = new AceEditor();
	AceEditor editorO = new AceEditor();
	Boolean fuzzy = false;
	String service = "";

	public static String readStringFromURL(String requestURL) throws IOException {
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(), StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	@Route("tabs-content")
	public class TabsContent extends Div {

		private final Tab fdt;
		private final Tab cdt;
		private final Tab turtle;
		private final VerticalLayout content;

		public TabsContent() {
			this.getStyle().set("width", "100%");
			fdt = new Tab("Fuzzy Dataset");
			cdt = new Tab("Crisp Dataset");
			turtle = new Tab("Turtle View");

			Tabs tabs = new Tabs(fdt, cdt, turtle);
			tabs.addSelectedChangeListener(event -> setContent(event.getSelectedTab()));

			content = new VerticalLayout();
			content.setSpacing(false);
			setContent(tabs.getSelectedTab());

			add(tabs, content);
		}

		private void setContent(Tab tab) {
			content.removeAll();

			if (tab.equals(fdt)) {
				content.add(ldataset_fuzzy);
			} else if (tab.equals(cdt)) {
				content.add(ldataset_crisp);
			} else {
				content.add(editorO);
			}
		}

	}

	public MainView() {

		VaadinSession.getCurrent().setErrorHandler(new CustomErrorHandler());

		final VerticalLayout layout = new VerticalLayout();
		layout.getStyle().set("width", "100%");
		layout.getStyle().set("background", "#F8F8F8");

		Image lab = new Image("img/banner-fsa.png", "banner");
		lab.setWidth("100%");
		lab.setHeight("150px");

		HorizontalLayout lfile = new HorizontalLayout();
		Label ds = new Label("URL Dataset (Type one or Select an Example)");

		TextField file = new TextField();
		file.setWidth("100%");

		Button download = new Button("Load Dataset");
		download.getStyle().set("width", "100pt");

		lfile.add(file);
		lfile.add(download);
		lfile.getStyle().set("width", "100%");

		TabsContent tabs = new TabsContent();
		RadioButtonGroup<String> radioGroup = new RadioButtonGroup<>();
		radioGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
		radioGroup.setLabel("Select Type of Resource");
		radioGroup.setItems("RDF/XML", "JSON", "TURTLE", "DBPEDIA", "WIKIDATA");
		radioGroup.getStyle().set("font-size", "80%");
		radioGroup.setValue("RDF/XML");

		Label fsaq = new Label();
		fsaq.add(new Html("<b style='font-size:150%; background:black; color:white;'>FSA-SPARQL Query</b>"));
		Label dt = new Label();
		dt.add(new Html("<b style='font-size:150%; background:black; color:white;'>Dataset</b>"));
		Label fdt = new Label();
		fdt.add(new Html("<b style='font-size:100%;'>Fuzzy RDF</b>"));
		Label cdt = new Label();
		cdt.add(new Html("<b style='font-size:100%;'>Crisp RDF</b>"));
		Label cv = new Label();
		cv.add(new Html("<b style='font-size:150%;'>SPARQL Crisp Version</b>"));

		Button run = new Button("Execute");
		run.setWidth("100%");
		run.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		VerticalLayout edS = new VerticalLayout();
		VerticalLayout edP = new VerticalLayout();

		editor.setHeight("300px");
		editor.setWidth("100%");
		editor.setFontSize(18);
		editor.setMode(AceMode.sparql);
		editor.setTheme(AceTheme.eclipse);
		editor.setUseWorker(true);
		editor.setReadOnly(false);
		editor.setShowInvisibles(false);
		editor.setShowGutter(false);
		editor.setShowPrintMargin(false);
		editor.setSofttabs(false);

		editorO.setHeight("300px");
		editorO.setWidth("100%");
		editorO.setFontSize(18);
		editorO.setMode(AceMode.turtle);
		editorO.setTheme(AceTheme.eclipse);
		editorO.setUseWorker(true);
		editorO.setReadOnly(false);
		editorO.setShowInvisibles(false);
		editorO.setShowGutter(false);
		editorO.setShowPrintMargin(false);
		editorO.setSofttabs(false);

		String quanA = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Actor \n" + "WHERE {\n"
				+ "  ?Movie movie:actor ?Actor .\n" + "  ?Movie f:type (movie:quality movie:Excellent ?tg) . \n"
				+ "  ?Movie f:type (movie:genre movie:Drama ?tt)\n" + "}\n" + "GROUP BY ?Actor \n"
				+ "HAVING (f:QUANT(f:AND_PROD(?tg,?tt)>0.7) > 0.8)\n"
				+ "# ACTORS AND ACTRESS WHO MOST OF MOVIES ARE EXCELLENT AND DRAMA";

		String quanB = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Actor \n" + "WHERE {\n"
				+ "   ?Movie movie:actor ?Actor .\n" + "   ?Movie f:type (movie:genre movie:Thriller ?ta)\n" + "}\n"
				+ "GROUP BY ?Actor\n" + "HAVING (f:QUANT(?ta>0.8)>0.8)\n"
				+ "# ACTORS AND ACTRESS WHO MOST OF MOVIES ARE THRILLERS ";

		String quanC = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Director \n" + "WHERE {\n"
				+ "   ?Movie movie:directed_by ?Director .\n"
				+ "   ?Movie f:type (movie:quality movie:Excellent ?tg) .\n"
				+ "   ?Movie f:type (movie:genre movie:Drama ?tt)\n" + "}\n" + "GROUP BY ?Director \n"
				+ "HAVING (f:QUANT(?tg>0.7)>0.8)\n" + "# DIRECTORS WHO MOST OF DRAMA MOVIES ARE EXCELLENT";

		String quanD = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Director  \n" + "WHERE {\n"
				+ "   ?Movie movie:directed_by ?Director .\n"
				+ "   ?Movie f:type (movie:quality movie:Excellent ?tg) .\n"
				+ "   ?Movie f:type (movie:genre movie:Comedy ?tt) .\n" + "   FILTER(?tt>0.7)\n" + "}\n"
				+ "GROUP BY ?Director \n" + "HAVING (f:QUANT(?tg>0.7) > 0.8)\n"
				+ "# DIRECTORS WHO MOST OF VERY COMIC MOVIES ARE EXCELLENT ";

		String aggA = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ( f:FCOUNT()  as ?total )\n" + "WHERE {\n"
				+ "   ?Movie f:type (movie:genre movie:Comedy ?ta) \n" + "}\n" + "# NUMBER OF COMEDY MOVIES ";

		String aggB = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ( f:FCOUNT()  as ?total )\n" + "WHERE {\n"
				+ "   ?Movie f:type (movie:genre movie:Comedy ?ta) .\n"
				+ "   ?Movie f:type (movie:genre movie:Drama ?tb) \n" + "}\n" + "# NUMBER OF COMEDY AND DRAMA MOVIES ";

		String aggC = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ( f:FCOUNT()  as ?total )\n" + "WHERE {\n"
				+ "   ?Movie1 f:type (movie:genre movie:Comedy ?ta) .\n"
				+ "   ?Movie2 f:type (movie:genre movie:Drama ?tb) \n" + "}\n"
				+ "# NUMBER OF COMEDY MOVIES MULTIPLY BY THE NUMBER OF DRAMA MOVIES";

		String aggD = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT (f:FSUM(?Duration) as ?total) \n" + "WHERE {\n"
				+ "  ?Movie f:type (movie:genre movie:Comedy ?ta) .\n" + "  ?Movie movie:duration ?Duration \n" + "}\n"
				+ "# TOTAL DURATION OF COMEDY MOVIES";

		String aggE = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Type (f:FSUM(?Duration) as ?total) \n" + "WHERE {\n"
				+ "    ?Movie f:type (movie:genre movie:Comedy ?ta) .\n" + "    ?Movie movie:duration ?Duration . \n"
				+ "    BIND(IF(?ta>0.7,'Comedy','Non Comedy') AS ?Type)\n" + "}\n" + "GROUP BY ?Type \n"
				+ "# TOTAL DURATION OF COMEDY AND NON COMEDY MOVIES";

		String aggF = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT  (f:FAVG(?Duration) as ?total) \n" + "WHERE {\n"
				+ "    ?Movie f:type (movie:genre movie:Comedy ?ta) .\n" + "    ?Movie movie:duration ?Duration\n"
				+ "}\n" + "# AVERAGE DURATION OF COMEDY MOVIES";

		String aggG = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Movie ?Duration \n" + "WHERE {\n"
				+ "   ?Movie f:type (movie:genre movie:Comedy ?ta) .\n" + "   ?Movie movie:duration ?Duration .\n"
				+ "   FILTER (f:WEIGHT(?Duration)=?total) .\n" + "             {\n"
				+ "              SELECT (f:FMAX(?Duration) as ?total)\n" + "              WHERE {\n"
				+ "              ?Movie f:type (movie:genre movie:Comedy ?ta) .\n"
				+ "              ?Movie movie:duration ?Duration\n" + "                    }\n" + "             }\n"
				+ "}\n" + "# THE DURATION OF THE MOST COMIC MOVIE";

		String aggH = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Actor (f:FCOUNT() as ?total)\n" + "WHERE {\n"
				+ "       ?Movie f:type (movie:genre movie:Drama ?t) .\n" + "       ?Movie movie:actor ?Actor\n" + "}\n"
				+ "GROUP BY ?Actor\n" + "# NUMBER OF DRAMA MOVIES BY ACTOR AND ACTRESS";

		String aggI = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX movie: <http://www.movies.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Movie ?Duration \r\n" + "WHERE {\r\n"
				+ "       ?Movie f:type (movie:genre movie:Drama ?ts) .\r\n"
				+ "       ?Movie movie:duration ?Duration .\r\n" + "       FILTER (f:WEIGHT(?Duration)=?min) .\r\n"
				+ "           {\r\n" + "             SELECT (f:FMIN(?Duration) as ?min)\r\n"
				+ "             WHERE {\r\n" + "             ?Movie f:type (movie:genre movie:Drama ?tk) .\r\n "
				+ "             ?Movie movie:duration ?Duration}\r\n" + "           }\n" + "      }\r\n"
				+ "# DURATION OF THE LESSER DRAMATIC MOVIE";

		String aggJ = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + "PREFIX hotel: <http://www.hotels.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "SELECT ?Quality (f:FAVG(?Price) as ?total)\r\n"
				+ "WHERE {\r\n" + "      ?Movie f:type (hotel:quality ?Quality ?ta) .\r\n"
				+ "      VALUES ?Quality {hotel:Bad hotel:Average hotel:Good} .\r\n"
				+ "      ?Movie hotel:price ?Price .\r\n" + "}\r\n" + "GROUP BY ?Quality\r\n"
				+ "# AVERAGE PRICE OF HOTELS GROUP BY QUALITY";

		String basicA = "PREFIX movie: <http://www.movies.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?Rank \r\n" + "WHERE {\n"
				+ "           ?Movie movie:name ?Name . \r\n"
				+ "           ?Movie movie:leading_role (?Actor ?l) . \r\n"
				+ "           ?Actor movie:name \"George Clooney\". \r\n"
				+ "           ?Movie f:type (movie:quality movie:Good ?r) . \r\n"
				+ "           BIND(f:AND_PROD(?r,?l) as ?Rank) . \r\n" + "           FILTER (?Rank > 0.5)\n" + "}\n"
				+ "# GOOD MOVIES WHOSE LEADING ROLE IS GEORGE CLOONEY";

		String basicB = "PREFIX hotel: <http://www.hotels.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?Close \r\n" + "WHERE {\n"
				+ "  ?Hotel hotel:name ?Name . \r\n" + "  ?Hotel rdf:type hotel:Hotel . \r\n"
				+ "  ?Hotel hotel:close (?pi ?Close) . \r\n" + "  ?pi hotel:name \"Empire State Building\" \r\n"
				+ "  FILTER (?Close > 0.75)\n" + "}\n" + "# CLOSEST HOTELS TO THE EMPIRE STATE BUILDING";

		String basicC = "PREFIX hotel: <http://www.hotels.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?td \r\n" + "WHERE {\r\n"
				+ "  ?Hotel hotel:name ?Name .\r\n" + "  ?Hotel rdf:type hotel:Hotel .\r\n"
				+ "  ?Hotel hotel:price ?p \r\n" + "  BIND(f:AT_MOST(?p,200,300) as ?td)\n" + "  FILTER(?td >= 0.5)"
				+ "}\n" + "# HOTEL OF PRICE AT MOST 200 DOLARS";

		String basicD = "PREFIX hotel: <http://www.hotels.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?td \r\n" + "WHERE {\n"
				+ "  ?Hotel hotel:name ?Name . \r\n" + "  ?Hotel rdf:type hotel:Hotel . \r\n"
				+ "  ?Hotel hotel:price ?p . \r\n" + "  ?Hotel f:type (hotel:quality hotel:Good ?g) . \r\n"
				+ "  ?Hotel f:type (hotel:style hotel:Elegant ?e) \r\n"
				+ "  BIND(f:WSUM(0.1,f:AND_PROD(f:MORE_OR_LESS(?e), \r\n"
				+ "           f:VERY(?g)),0.9,f:CLOSE_TO(?p,100,50)) as ?td) .\r\n" + "  FILTER(?td > 0.7)\n" + "}\n"
				+ "# HOTELS VERY GOOD AND MORE OR LESS ELEGANT WITH PRICE CLOSE TO 100";

		String basicE = "PREFIX hotel: <http://www.hotels.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?td ?close1 ?close2 \r\n" + "WHERE {\n"
				+ "  ?Hotel hotel:name ?Name . \r\n" + "  ?Hotel rdf:type hotel:Hotel . \r\n" + "  {\n"
				+ "    ?Hotel hotel:close (?close1 ?td) . \r\n" + "    ?pi hotel:name \"Empire State Building\"\n"
				+ "  }\n" + "UNION\n" + "  {\n" + "    ?Hotel hotel:close (?close2 ?td) .\n"
				+ "    ?pi2 hotel:name \"Central Park\" \n" + "  }\r\n" + "FILTER(?td >0.7)\n" + "}\n"
				+ "# HOTELS CLOSE TO EMPIRE STATE BUILDING OR CENTRAL PARK";

		String basicF = "PREFIX hotel: <http://www.hotels.org#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Name ?td1 ?td2 ?close1 ?close2 \r\n" + "WHERE {\n"
				+ "   ?Hotel hotel:name ?Name . \r\n" + "   ?Hotel rdf:type hotel:Hotel . \r\n"
				+ "   ?Hotel hotel:close (?close1 ?td1) . \r\n" + "   ?pi hotel:name \"Empire State Building\" .\n"
				+ "   FILTER(?td1 >0.7)\n" + "OPTIONAL\n" + "   {\n" + "       ?Hotel hotel:close (?close2 ?td2) .\n"
				+ "       ?pi2 hotel:name \"Central Park\"  \r\n" + "       FILTER(?td2 >0.7)\n" + "   }\n" + "}\n"
				+ "# HOTELS CLOSE TO THE EMPIRE STATE BUILDING AND OPTIONALLY CLOSE TO CENTRAL PARK";

		// http://minerva.ual.es:8080/api.social/twitterSearchTweets/q/Ukrania&option=hashtag&count=30

		String twA = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?text ?screen_name ?retweets ?td \n" + " WHERE\n"
				+ " {\n" + "   ?s rdf:type json:item .\n" + "   ?s json:text ?text .\n" + "   ?s json:user ?u .\n"
				+ "   ?u json:screen_name ?screen_name .\n" + "   ?s f:type (json:retweet_count fs:High ?td) .\n"
				+ "   ?s json:retweet_count ?retweets .\n" + "   FILTER( ?td  >= 0.8)\n" + " }\n"
				+ "# TWEETS ABOUT UKRANIA WITH A HIGH NUMBER OF RETWEETS";

		// http://minerva.ual.es:8080/api.social/twitterSearchUser/q/Obama

		String twB = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?name ?screen_name ?td \n" + "WHERE\n" + " {\n"
				+ "   ?s json:screen_name ?screen_name .\n" + "   ?s json:name ?name .\n"
				+ "   ?s f:type (json:followers_count fs:High ?td) \n" + "   FILTER(?td>=0.8)\n" + " }\n"
				+ "# TWITTER ACCOUNTS ABOUT SPAIN WITH A HIGH NUMBER OF FOLLOWERS ";

		// http://minerva.ual.es:8080/api.social//twitterUserTimeLine/screen_name/bbcmundo

		String twC = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?text ?retweets ?td \n" + "WHERE\n" + " {\n"
				+ "      ?s rdf:type json:item .\n" + "      ?s json:text ?text .\n"
				+ "      ?s f:type (json:retweet_count fs:High ?td) .\n" + "      ?s json:retweet_count ?retweets .\n"
				+ "      FILTER(?td > 0.8)\n" + "}\n" + "# TWEETS OF THE BBC ACCOUNT WITH A HIGH NUMBER OF RETWEETS ";

		// http://minerva.ual.es:8080/api.social/youtubeVideoSearch/q/Messi

		String ytA = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Title ?td\n" + " WHERE\n" + " {\n"
				+ "   ?s rdf:type json:item .\n" + "   ?s json:snippet ?sn .\n" + "   ?sn  json:title ?Title .\n"
				+ "   ?s json:statistics ?st .\n" + "   ?st f:type (json:viewCount fs:High ?td) .\n"
				+ "   FILTER(?td >= 0.8) " + " }\n" + "# VIDEOS OF YOUTUBE ABOUT MESSI WITH A HIGH NUMBER OF VIEWS";

		// http://minerva.ual.es:8080/api.social/youtubeChannelSearch/q/Messi

		String ytB = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Title ?td\n" + " WHERE\n" + " {\n"
				+ "   ?s rdf:type json:item .\n" + "   ?s json:snippet ?sn .\n" + "   ?sn  json:title ?Title .\n"
				+ "   ?s json:statistics ?st .\n" + "   ?st f:type (json:subscriberCount fs:High ?td) .\n"
				+ "   FILTER(?td >= 0.8) " + " }\n"
				+ "# CHANNELS OF YOUTUBE ABOUT MESSI WITH A HIGH NUMBER OF SUBSCRIBERS ";

		// http://minerva.ual.es:8080/api.social/tmdbSearchMovies/q/Alien

		String tmdbA = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Title ?td\n" + "WHERE\n" + " {\n"
				+ "   ?s f:type (json:popularity fs:High ?td) .\n" + "   ?s json:title ?Title .\n"
				+ "   FILTER(?td >= 0.8)" + " }\n" + "# MOST POPULAR ALIEN MOVIES";

		// http://minerva.ual.es:8080/api.social/tmdbSearchMovies/q/Peter

		String tmdbB = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Name ?td\n" + "WHERE\n" + " {\n"
				+ "   ?s json:name ?Name .\n" + "   ?s f:type (json:popularity fs:High ?td) .\n"
				+ "   FILTER(?td >= 0.8)\n " + " }\n" + "# MOST POPULAR ACTORS CALLED PETER";

		// https://datos.unican.es/docencia/3223/asignaturas-de-master-2022-2023.json

		String ldA = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Title ?BachelorsDegree ?td \n" + "WHERE\n" + " {\n"
				+ "   ?s json:title ?Title .\n" + "   ?s json:BacherlorsDegree ?BachelorsDegree .\n"
				+ "   ?s f:type (json:courseCredits fs:High ?td) .\n" + "   FILTER(?td > 0.6)\n" + " }"
				+ "# COURSES OF BACHELORS DEGREES IN THE UNIVERSITY OF CANTABRIA WITH HIGH NUMBER OF CREDITS ";

		// https://do.diba.cat/api/dataset/museus/format/json

		String tdB = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				// + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
				+ "PREFIX json: <http://www.json.org#>\n" + "SELECT ?Address ?td\n" + "WHERE\n" + " {\n"
				+ "   ?s json:elements ?e .\n" + "   ?e json:adreca_nom ?Address .\n"
				+ "   ?e json:rel_municipis ?m .\n" + "   ?m f:type (json:nombre_habitants fs:High ?td) .\n"
				+ "   FILTER(?td >= 0.9)\n" + " }\n"
				+ "# MUSSEUMS IN CATALONIA LOCATED IN CITIES OF A HIGH NUMBER OF HABITANTS";

		// https://minerva.ual.es/fsafulldata/social.owl

		String owlA = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n" + "PREFIX fs: <http://www.fuzzysets.org#>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\n" + "PREFIX json: <http://www.json.org#>\n" + "SELECT ?User ?td\n"
				+ "WHERE\n" + " {\n" + "   ?User f:type (sn:age fs:High ?td) .\n" + "   FILTER(?td > 0.9)\n" + " }"
				+ "# OLDEST PEOPLE IN THE SOCIAL NETWORK";

		// https://minerva.ual.es/fsafulldata/course.owl

		String owlB =
				// "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
						// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
						+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
						+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
						// + "PREFIX json: <http://www.json.org#>\n"
						+ "SELECT ?Student ?td\n" + "WHERE\n" + " {\n"
						+ "   ?Student <http://www.semanticweb.org/course#is_enrolled> ?enrolled .\n"
						+ "   ?enrolled f:type (<http://www.semanticweb.org/course#scores> fs:High ?td)\n"
						+ "   FILTER(?td > 0.8)\n" + " }\n" + "# STUDENTS WITH THE HIGHEST SCORES";

		// https://minerva.ual.es/fsafulldata/food.owl

		String owlC =
				// "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
						// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
						+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
						+ "PREFIX fs: <http://www.fuzzysets.org#>\n" + "PREFIX f: <http://www.fuzzy.org#>\n"
						+ "PREFIX food: <http://www.semanticweb.org/food#>\n"
						// + "PREFIX json: <http://www.json.org#>\n"
						+ "SELECT ?Menu ?Dish ?td ?td2\n" + "WHERE\n" + " {\n"
						+ "    ?Menu f:type (food:price fs:Low ?td) .\n" + "    ?Menu food:dish ?Dish .\n"
						+ "    ?Dish f:type (food:time fs:Low ?td2) .\n" + "    FILTER(f:AND_PROD(?td,?td2) >= 0.8) .\n"
						+ " }\n" + "# MENUS WITH THE LOWEST PRICE AND DISHES WITH LOW TIME OF COOKING";

		String wdA = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX p: <http://www.wikidata.org/prop/>\r\n" + "PREFIX wd: <http://www.wikidata.org/entity/>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n" + "\r\n"
				+ "SELECT ?wdLabel ?td ?td2 \r\n" + "WHERE \r\n" + "{" + "  ?city rdfs:label ?wdLabel .\r\n"
				+ "  FILTER (lang(?wdLabel) = 'es')\r\n" + "  ?city wdt:P31 wd:Q515 .\r\n"
				+ "  ?city f:type (wdt:P1082 fs:High ?td) .\r\n" + "  ?city f:type (wdt:P2046 fs:High ?td2) .\r\n"
				+ "  ?city wdt:P17 wd:Q29 .\r\n" + "  FILTER(f:OR_PROD(?td,?td2)>=0.7)\r\n" + "}\n"
				+ "# CITIES OF SPAIN WITH HIGH POPULARION OR HIGH AREA ";

		String wdB = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX p: <http://www.wikidata.org/prop/>\r\n" + "PREFIX wd: <http://www.wikidata.org/entity/>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT ?wdLabel ?td \r\n" + "WHERE\r\n" + "{" + "   ?country wdt:P31 wd:Q6256 .\r\n"
				+ "   ?country f:type  (wdt:P1082 fs:High ?td) .\r\n" + "   ?country rdfs:label ?wdLabel .\r\n"
				+ "   FILTER (lang(?wdLabel) = 'en')\r\n" + "   FILTER(?td>=0.8)\r\n" + "}\n"
				+ "# COUNTRIES WITH HIGH POPULATION";

		String dbpA = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\r\n" + "PREFIX dbp: <http://dbpedia.org/property/>\r\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\r\n" + "PREFIX f: <http://www.fuzzy.org#>\r\n"
				+ "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				// + "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>\r\n"
				+ "SELECT  ?teatre ?od ?scap ?cap " + "WHERE { \r\n" + "      ?teatre rdf:type dbo:Theatre .\r\n"
				+ "      ?teatre dbo:openingDate ?od .\r\n"
				+ "      ?teatre f:type (dbo:seatingCapacity fs:High ?cap) .\r\n"
				+ "      ?teatre dbo:seatingCapacity ?scap .\r\n" + "      ?teatre dbo:city dbr:New_York_City .\r\n"
				+ "FILTER(?cap >= 0.8) .\r\n" + "FILTER(fs:SET(year(?od) >=1920))\r\n" + "}\n"
				+ "# THEATRES WITH THE HIGHEST CAPACITY AMONG BUILT AFTER 1920";

		String dbpB = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\r\n" + "PREFIX dbp: <http://dbpedia.org/property/>\r\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\r\n" + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\r\n"
				// + "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "SELECT  ?city_name ?pop ?area \r\n" + "WHERE {\r\n" + "    ?city rdf:type dbo:City ;\r\n"
				+ "    foaf:name ?city_name ;\r\n" + "    dbo:country dbr:Canada ;\r\n"
				+ "    f:type (dbp:populationTotal fs:High ?pop) ;\r\n"
				+ "    f:type (dbo:areaTotal fs:Low ?area) .\r\n" + "    FILTER(f:AND_PROD(?pop,?area)>=0.7) " + "}\n"
				+ "# CITIES OF CANADA WITH HIGH POPULATION AND LOW AREA";

		String dbpC = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT ?actor ?ch ?nch \r\n" + "WHERE {\r\n"
				+ "   ?actor rdf:type <http://dbpedia.org/class/yago/WikicatActors> .\r\n"
				+ "   ?actor dbo:birthPlace ?place .\r\n" + "   ?actor f:type (dbp:children fs:High ?ch) .\r\n"
				+ "   ?actor dbp:children ?nch .\r\n" + "   ?place dbo:country dbr:Spain .\r\n"
				+ "   FILTER(?ch >= 0.7)\r\n" + "}\n" + "# ACTORS OF SPAIN WITH A HIGH NUMBER OF CHILDREN";

		String dbpD = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				// + "PREFIX dbc: <http://dbpedia.org/resource/Category>\n"
				// + "PREFIX dct: <http://purl.org/dc/terms/subject>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT ?team ?player ?cap\r\n" + "WHERE {\r\n" + "   ?team rdf:type dbo:SoccerClub .\r\n"
				+ "   ?player f:type (dbo:height fs:High ?cap) .\r\n" + "   ?player dbo:team ?team  .\r\n"
				+ "   ?player dbo:birthPlace ?bp .\r\n" + "   ?bp dbo:country dbr:Spain .\r\n"
				+ "   ?team <http://purl.org/dc/terms/subject> <http://dbpedia.org/resource/Category:Premier_League_clubs> .\r\n"
				+ "   ?player <http://purl.org/dc/terms/subject> <http://dbpedia.org/resource/Category:La_Liga_players> .\r\n"
				+ "   FILTER(?cap >= 0.8)\r\n" + "}\n"
				+ "# THE TALLEST PLAYERS OF THE PREMIER LEAGUE WHO PLAYED ALSO IN LA LIGA ";

		String dbpE = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				// + "PREFIX dbc: <http://dbpedia.org/resource/Category>\n"
				// + "PREFIX dct: <http://purl.org/dc/terms/subject>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT  ?journal ?fac  \r\n" + "WHERE { \r\n"
				+ "        ?journal a <http://dbpedia.org/ontology/AcademicJournal> . \r\n"
				+ "        ?journal f:type (dbo:impactFactor fs:High ?fac) .	\r\n"
				+ "        ?journal dbo:academicDiscipline <http://dbpedia.org/resource/Computer_science>\r\n"
				+ "        FILTER(?fac >= 0.5)\r\n" + "} \r\n" + "# COMPUTER SCIENCE JOURNALS WITH THE HIGHEST IMPACT";

		String dbpF = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				// + "PREFIX dbc: <http://dbpedia.org/resource/Category>\n"
				// + "PREFIX dct: <http://purl.org/dc/terms/subject>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT ?plant ?vc ?fiber ?prot \r\n" + "WHERE {\r\n" + "        ?plant rdf:type dbo:Plant . \r\n"
				+ "        ?plant f:type (dbp:protein fs:High ?prot) .	\r\n"
				+ "        ?plant f:type (dbp:vitcMg fs:High ?vc) .	\r\n"
				+ "        ?plant f:type (dbp:fiber fs:High ?fiber) .	\r\n"
				+ "        FILTER(f:OR_GOD(?vc,f:OR_GOD(?fiber,?prot))>=0.7)\r\n" + "}\r\n"
				+ "# PLANTS WITH HIGH LEVEL OF PROTEINS OR FIBER OR VITAMINE C";

		String dbpG = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				// + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "SELECT *\r\n" + "WHERE {\r\n" + "{\r\n" + "SELECT ?community (f:QUANT(?pu>=0.5) as ?pop)\r\n "
				+ "  WHERE\r\n" + "     {\r\n" + "       ?community dbo:type dbr:Autonomous_communities_of_Spain .\r\n"
				+ "       ?p dbo:subdivision ?community .\r\n"
				+ "       ?p f:type (dbo:populationUrban fs:High ?pu) .\r\n" + "     }\r\n" + "  GROUP BY ?community\n"
				+ "}\r\n" + "FILTER (?pop >= 0.7)\n" + "}\r\n"
				+ "# THE AUTONOMOUS COMMUNITY OF SPAIN IN WICH MOST OF CITIES HAVE A LARGE POPULATION";

		String dbpH = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX dbo: <http://dbpedia.org/ontology/>\n" + "PREFIX dbp: <http://dbpedia.org/property/>\n"
				+ "PREFIX dbr: <http://dbpedia.org/resource/>\n"
				// + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX fs: <http://www.fuzzysets.org#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n" + "SELECT * \n" + "WHERE {\n" + "{\n"
				+ "SELECT ?actor (f:HIGH() as ?movies)\r\n WHERE\r\n" + "     {\r\n"
				+ "     ?actor rdf:type <http://dbpedia.org/class/yago/WikicatActors> .\r\n"
				+ "     ?actor dbo:birthPlace ?p .\r\n" + "     ?p dbo:country dbr:Spain .\r\n"
				+ "     ?movie dbp:starring ?actor .\r\n" + "     }\r\n " + "GROUP BY ?actor\r\n" + "}\r\n "
				+ "FILTER (?movies>=0.7)}\r\n" + "# ACTORS OF SPAIN STARRING A HIGH NUMBER OF MOVIES";

//		$app->get('/youtubeVideoSearch/q/{q}', 'youtubeVideoSearch');
//		$app->get('/youtubeChannelSearch/q/{q}', 'youtubeChannelSearch');
//		$app->get('/youtubePlaylistSearch/q/{q}', 'youtubePlaylistSearch');
//		$app->get('/twitterSearchTweets/q/{q}', 'twitterSearchTweets');
//		$app->get('/twitterRetweetsID/id/{id}', 'twitterRetweetsID');
//		$app->get('/twitterRetweetersID/id/{id}', 'twitterRetweetersID');
//		$app->get('/twitterUserTimeLine/screen_name/{screen_name}', 'twitterUserTimeLine');
//		$app->get('/twitterShowUser/screen_name/{screen_name}', 'twitterShowUser');
//		$app->get('/twitterShowUser/user_id/{user_id}', 'twitterShowUserID');
//		$app->get('/twitterFollowersID/screen_name/{screen_name}', 'twitterFollowersID');
//		$app->get('/twitterFollowersList/screen_name/{screen_name}', 'twitterFollowersList');
//		$app->get('/twitterFavoritesList/screen_name/{screen_name}', 'twitterFavoritesList');
//		$app->get('/twitterFriendsID/screen_name/{screen_name}', 'twitterFriendsID');
//		$app->get('/twitterFriendsList/screen_name/{screen_name}', 'twitterFriendsList');
//		$app->get('/twitterSearchUser/q/{q}', 'twitterSearchUser');
//		$app->get('/tmdbSearchMovies/q/{q}', 'tmdbSearchMovies');
//		$app->get('/tmdbSearchPeople/q/{q}', 'tmdbSearchPeople');
//		$app->get('/tmdbMovieID/movie_id/{movie_id}', 'tmdbMovieID');
//		$app->get('/tmdbPersonID/person_id/{person_id}', 'tmdbPersonID');
//		$app->get('/tmdbMovies/option/{option}', 'tmdbMovies');
//		$app->get('/tmdbPeople/option/{option}', 'tmdbPeople');

		AceEditor editorP = new AceEditor();
		editorP.setHeight("300px");
		editorP.setWidth("100%");
		editorP.setFontSize(18);
		editorP.setMode(AceMode.sparql);
		editorP.setTheme(AceTheme.eclipse);
		editorP.setUseWorker(true);
		editorP.setReadOnly(true);
		editorP.setShowInvisibles(false);
		editorP.setShowGutter(false);
		editorP.setShowPrintMargin(false);
		editorP.setSofttabs(false);
		Grid<HashMap<String, Term>> answers = new Grid<HashMap<String, Term>>();
		answers.setWidth("100%");
		answers.setHeight("100%");
		// CAMBIAR
		answers.setVisible(true);

		radioGroup.addValueChangeListener(event -> {
			if (radioGroup.getValue() == "DBPEDIA") {
				dt.setVisible(false);
				tabs.setVisible(false);
				lfile.setVisible(false);
				ds.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (radioGroup.getValue() == "WIKIDATA") {
				dt.setVisible(false);
				tabs.setVisible(false);
				lfile.setVisible(false);
				ds.setVisible(false);
				fuzzy = true;
				service = "https://query.wikidata.org/bigdata/namespace/wdq/sparql";
			} else {
				dt.setVisible(true);
				tabs.setVisible(true);
				lfile.setVisible(true);
				ds.setVisible(true);

			}
		});

		download.addClickListener(event -> {
			if (radioGroup.getValue() == "RDF/XML") {
				load_rdf(file.getValue());
				show_rdf();
			} else if (radioGroup.getValue() == "JSON") {
				load_json(file.getValue());
				show_rdf();
			} else if (radioGroup.getValue() == "WIKIDATA") {
			} else if (radioGroup.getValue() == "DBPEDIA") {
			} else {
				load_ttl(file.getValue());
				show_rdf();
			}

			// CAMBIAR
			// answers.setVisible(false);
			// editorP.setVisible(false);
			// cv.setVisible(false);
		});

		ldataset_fuzzy.setWidth("100%");
		ldataset_fuzzy.setHeight("200pt");
		ldataset_crisp.setWidth("100%");
		ldataset_crisp.setHeight("200pt");

		dataset_fuzzy.setWidth("100%");
		dataset_fuzzy.setHeight("100%");
		dataset_fuzzy.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

		dataset_crisp.setWidth("100%");
		dataset_crisp.setHeight("100%");
		dataset_crisp.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

		file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");

		load_rdf(file.getValue());
		show_rdf();
		editor.setValue(basicA);

		// NEW
		MenuBar menuBar = new MenuBar();
		menuBar.setWidth("100%");
		ComponentEventListener<ClickEvent<MenuItem>> listener = e -> {
			if (e.getSource().getText().equals("Quantification A")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(quanA);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Quantification B")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(quanB);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Quantification C")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(quanC);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Quantification D")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(quanD);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation A")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggA);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation B")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggB);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation C")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggC);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregartion D")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggD);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation E")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggE);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation F")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggF);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation G")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggG);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation H")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggH);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation I")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggI);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic A")) {
				file.setValue("https://minerva.ual.es/fsafulldata/movies-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicA);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Aggregation J")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(aggJ);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic B")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicB);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic C")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicC);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic D")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicD);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic E")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicE);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Basic F")) {
				file.setValue("https://minerva.ual.es/fsafulldata/hotels-fuzzy.rdf");
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(basicF);
				// answers.setVisible(false);
				// editorP.setVisible(false);

			} else if (e.getSource().getText().equals("Twitter A")) {
				file.setValue(
						"http://minerva.ual.es:8080/api.social/twitterSearchTweets/q/Ukrania&option=hashtag&count=30");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(twA);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Twitter B")) {
				file.setValue("http://minerva.ual.es:8080/api.social/twitterSearchUser/q/España");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(twB);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Twitter C")) {
				file.setValue("http://minerva.ual.es:8080/api.social//twitterUserTimeLine/screen_name/bbcmundo");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(twC);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Youtube A")) {
				file.setValue("http://minerva.ual.es:8080/api.social/youtubeVideoSearch/q/Messi");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(ytA);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Youtube B")) {
				file.setValue("http://minerva.ual.es:8080/api.social/youtubeChannelSearch/q/Messi");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(ytB);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("TMDB A")) {
				file.setValue("http://minerva.ual.es:8080/api.social/tmdbSearchMovies/q/Alien");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(tmdbA);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("TMDB B")) {
				file.setValue("http://minerva.ual.es:8080/api.social/tmdbSearchPeople/q/Peter");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(tmdbB);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Linked Data A")) {
				file.setValue("https://datos.unican.es/docencia/3223/asignaturas-de-master-2022-2023.json");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(ldA);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Linked Data B")) {
				file.setValue("https://do.diba.cat/api/dataset/museus/format/json");
				radioGroup.setValue("JSON");

				cv.setVisible(false);
				load_json(file.getValue());
				show_rdf();
				editor.setValue(tdB);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Ontologies C")) {
				file.setValue("https://minerva.ual.es/fsafulldata/food.owl");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(owlC);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Ontologies B")) {
				file.setValue("https://minerva.ual.es/fsafulldata/course.owl");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(owlB);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Ontologies A")) {
				file.setValue("https://minerva.ual.es/fsafulldata/social.owl");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(owlA);
				// answers.setVisible(false);
				// editorP.setVisible(false);
			} else if (e.getSource().getText().equals("Wikidata A")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("WIKIDATA");
				editor.setValue(wdA);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://query.wikidata.org/bigdata/namespace/wdq/sparql";
			} else if (e.getSource().getText().equals("Wikidata B")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("WIKIDATA");
				editor.setValue(wdB);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://query.wikidata.org/bigdata/namespace/wdq/sparql";
			} else if (e.getSource().getText().equals("DBpedia A")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpA);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia B")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpB);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia C")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpC);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia D")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpD);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia E")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpE);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia F")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpF);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia G")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpG);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			} else if (e.getSource().getText().equals("DBpedia H")) {
				file.setValue("");
				autocompletion();

				cv.setVisible(false);
				radioGroup.setValue("DBPEDIA");
				editor.setValue(dbpH);
				answers.setVisible(false);
				editorP.setVisible(false);
				dt.setVisible(false);
				tabs.setVisible(false);
				fuzzy = true;
				service = "https://dbpedia.org/sparql/";
			}

		}

		;

		MenuItem basic = menuBar.addItem("Basic", listener);
		SubMenu basicSubMenu = basic.getSubMenu();
		basicSubMenu.addItem("Basic A", listener);
		basicSubMenu.addItem("Basic B", listener);
		basicSubMenu.addItem("Basic C", listener);
		basicSubMenu.addItem("Basic D", listener);
		basicSubMenu.addItem("Basic E", listener);
		basicSubMenu.addItem("Basic F", listener);

		MenuItem agg = menuBar.addItem("Aggregation", listener);
		SubMenu aggSubMenu = agg.getSubMenu();
		aggSubMenu.addItem("Aggregation A", listener);
		aggSubMenu.addItem("Aggregation B", listener);
		aggSubMenu.addItem("Aggregation C", listener);
		aggSubMenu.addItem("Aggregation D", listener);
		aggSubMenu.addItem("Aggregation E", listener);
		aggSubMenu.addItem("Aggregation F", listener);
		aggSubMenu.addItem("Aggregation G", listener);
		aggSubMenu.addItem("Aggregation H", listener);
		aggSubMenu.addItem("Aggregation I", listener);
		aggSubMenu.addItem("Aggregation J", listener);

		MenuItem quan = menuBar.addItem("Quantification", listener);
		SubMenu quanSubMenu = quan.getSubMenu();
		quanSubMenu.addItem("Quantification A", listener);
		quanSubMenu.addItem("Quantification B", listener);
		quanSubMenu.addItem("Quantification C", listener);
		quanSubMenu.addItem("Quantification D", listener);

		MenuItem social = menuBar.addItem("Social Networks", listener);
		SubMenu socialSubMenu = social.getSubMenu();
		socialSubMenu.addItem("Twitter A", listener);
		socialSubMenu.addItem("Twitter B", listener);
		socialSubMenu.addItem("Twitter C", listener);
		socialSubMenu.addItem("Youtube A", listener);
		socialSubMenu.addItem("Youtube B", listener);
		socialSubMenu.addItem("TMDB A", listener);
		socialSubMenu.addItem("TMDB B", listener);

		MenuItem ld = menuBar.addItem("Linked Data", listener);
		SubMenu ldSubMenu = ld.getSubMenu();
		ldSubMenu.addItem("Linked Data A", listener);
		ldSubMenu.addItem("Linked Data B", listener);

		MenuItem ont = menuBar.addItem("Ontologies", listener);
		SubMenu ontSubMenu = ont.getSubMenu();
		ontSubMenu.addItem("Ontologies A", listener);
		ontSubMenu.addItem("Ontologies B", listener);
		ontSubMenu.addItem("Ontologies C", listener);

		MenuItem dbp = menuBar.addItem("DBpedia", listener);
		SubMenu dbpSubMenu = dbp.getSubMenu();
		dbpSubMenu.addItem("DBpedia A", listener);
		dbpSubMenu.addItem("DBpedia B", listener);
		dbpSubMenu.addItem("DBpedia C", listener);
		dbpSubMenu.addItem("DBpedia D", listener);
		dbpSubMenu.addItem("DBpedia E", listener);
		dbpSubMenu.addItem("DBpedia F", listener);
		dbpSubMenu.addItem("DBpedia G", listener);
		dbpSubMenu.addItem("DBpedia H", listener);

		MenuItem wd = menuBar.addItem("Wikidata", listener);
		SubMenu wdSubMenu = wd.getSubMenu();
		wdSubMenu.addItem("Wikidata A", listener);
		wdSubMenu.addItem("Wikidata B", listener);

		//FsaSPARQL fs = new FsaSPARQL();

		VerticalLayout lanswers = new VerticalLayout();
		lanswers.setWidth("100%");
		lanswers.setHeight("200pt");
		// CAMBIAR
		lanswers.setVisible(true);

		run.addClickListener(new ComponentEventListener<ClickEvent<Button>>() {

			@Override
			public void onComponentEvent(ClickEvent<Button> event) {
				// TODO Auto-generated method stub
				step = 0; // step++;
				pSPARQL ps = new pSPARQL();

				//FsaSPARQL fsa = new FsaSPARQL();
				//String s = fsa.FSAtoSPARQL(editor.getValue(), false);
				
				String s = editor.getValue();

				List<List<String>> rules = null;
				rules = ps.SPARQLtoProlog(s, step);

				String pp = "";
				String prule = "";
				for (List<String> r : rules) {
					prule = r.get(0) + ":-";
					for (int i = 1; i < r.size(); i++) {
						prule = prule + "\n       " + r.get(i) + ",";
					}
					prule = prule.substring(0, prule.length() - 1) + ".";
					pp = pp + "\n" + prule;

				}

				/*
				 * PARA PODER ACTUALIZAR EL EDITOR String content = editorOntology.getValue();
				 * String path = file.getValue(); try { Files.write( Paths.get(path),
				 * content.getBytes(), StandardOpenOption.CREATE); } catch (IOException e) { //
				 * TODO Auto-generated catch block e.printStackTrace(); }
				 */

				String t1 = "use_module(library(semweb/rdf11))";
				org.jpl7.Query q1 = new org.jpl7.Query(t1);
				System.out.print((q1.hasSolution() ? "" : ""));
				q1.close();

				String t11 = "use_module(library(semweb/rdf_http_plugin))";
				org.jpl7.Query q11 = new org.jpl7.Query(t11);
				System.out.print((q11.hasSolution() ? "" : ""));
				q11.close();

				String t12 = "use_module(library(lists))";
				org.jpl7.Query q12 = new org.jpl7.Query(t12);
				System.out.print((q12.hasSolution() ? "" : ""));
				q12.close();

				String t21b = "rdf_reset_db";
				org.jpl7.Query q21b = new org.jpl7.Query(t21b);
				System.out.print((q21b.hasSolution() ? "" : ""));
				q21b.close();

				String t21bb = "rdf_reset_db";
				org.jpl7.Query q21bb = new org.jpl7.Query(t21bb);
				System.out.print((q21bb.hasSolution() ? "" : ""));
				q21bb.close();

				String t21c = " working_directory(_,\"C:/\")";
				org.jpl7.Query q21c = new org.jpl7.Query(t21c);
				System.out.print((q21c.hasSolution() ? "" : ""));
				q21c.close();

				String t2 = "rdf_load('" + "C:/tmp-sparql/model.rdf" + "')";
				org.jpl7.Query q2 = new org.jpl7.Query(t2);
				System.out.print((q2.hasSolution() ? "" : ""));
				q2.close();

				String t22 = "rdf(X,Y,Z)";
				org.jpl7.Query q22 = new org.jpl7.Query(t22);
				String rdfs = "";
				Map<String, Term>[] srdfs = q22.allSolutions();
				q22.close();

				for (Map<String, Term> solution : srdfs) {
					rdfs = rdfs + "rdf(" + solution.get("X") + ',' + solution.get("Y") + ',' + solution.get("Z")
							+ ").\n";
				}

				editorP.setValue(pp + '\n' + rdfs);

				String prule2 = "";
				System.out.println("Number of rules: " + rules.size());
				for (List<String> r : rules) {

					prule2 = r.get(0) + ":-";
					for (int i = 1; i < r.size(); i++) {
						prule2 = prule2 + r.get(i) + ',';
					}
					prule2 = prule2.substring(0, prule2.length() - 1);
					String aprule = "asserta((" + prule2 + "))";
					org.jpl7.Query q3 = new org.jpl7.Query(aprule);
					System.out.println((q3.hasSolution() ? aprule : ""));
					q3.close();

				}

				String[] ops = {
						"'http://www.w3.org/2001/XMLSchema#decimal'(X^^TX,Y^^'http://www.w3.org/2001/XMLSchema#decimal'):-!, Y=X ",
						"'http://jena.apache.org/ARQ/function#sqrt'(X^^TX,Y^^TX):-!, Y is sqrt(X) ",
						"if(X,Y,Z,T):-!,((X=1^^_)->T=Y;T=Z)",
						"call_function(X,Y,F,T):-!, X=..[_,TX,TYPE],Y=..[_,TY|_],NE=..[F,TX,TY],TAUX is NE,T=..['^^',TAUX,'http://www.w3.org/2001/XMLSchema#decimal']" };

				for (int i = 0; i < ops.length; i++) {
					String aprule = "asserta((" + ops[i] + "))";
					org.jpl7.Query q3 = new org.jpl7.Query(aprule);
					System.out.println((q3.hasSolution() ? aprule : ""));
					q3.close();
				}

				List<HashMap<String, Term>> rows = new ArrayList<>();

				answers.removeAllColumns();

				Atom t = new Atom("Null");
				org.jpl7.Query q3 = new org.jpl7.Query(rules.get(0).get(0));
				Map<String, Term>[] sols = q3.allSolutions();
				q3.close();

				for (Map<String, Term> solution : sols) {
					Set<String> sol = solution.keySet();
					for (String var : sol) {
						if (solution.get(var).isCompound()) {
							solution.put(var, solution.get(var).arg(1));
						}
						if (solution.get(var).isVariable()) {
							solution.put(var, t);
						}
					}
				}

				for (Map<String, Term> solution : sols) {
					rows.add((HashMap<String, Term>) solution);

				}
				System.out.println("Yes: answers " + sols.length);

				answers.setItems(rows);

				if (rows.size() > 0) {
					HashMap<String, Term> sr = rows.get(0);

					for (Map.Entry<String, Term> entry : sr.entrySet()) {
						answers.addColumn(h -> h.get(entry.getKey())).setHeader(entry.getKey());
					}
				}

				for (List<String> r : rules) {

					String dr = r.get(0);
					org.jpl7.Query drq = new org.jpl7.Query("retractall(" + dr + ")");
					System.out.println((drq.hasSolution() ? drq : ""));
					drq.close();

				}

				for (int i = 0; i < ops.length; i++) {
					String aprule = "retract((" + ops[i] + "))";
					org.jpl7.Query q4 = new org.jpl7.Query(aprule);
					System.out.println((q4.hasSolution() ? aprule : ""));
					q4.close();
				}

			}

		});

		edS.add(editor);
		edP.add(editorP);
		layout.add(lab);
		layout.add(new Label("Please select examples in each category..."));
		layout.add(menuBar);
		layout.add(ds);
		layout.add(lfile);
		layout.add(radioGroup);
		layout.add(dt);
		editorO.setReadOnly(true);

		layout.add(tabs);
		layout.add(fsaq);
		layout.add(edS);
		layout.add(run);
		lanswers.add(answers);
		layout.add(lanswers);
		layout.add(cv);
		layout.add(edP);
		// CAMBIAR
		cv.setVisible(true);
		editor.setLiveAutocompletion(true);
		// CAMBIAR
		editorP.setVisible(true);
		add(layout);
		this.setSizeFull();

	}

	public void show_notification(String type, String message) {
		Notification notification = Notification.show(type + " " + message);
		notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
		notification.setPosition(Notification.Position.MIDDLE);
	}

	public void load_rdf(String url) {
		model.removeAll();
		model.clearNsPrefixMap();
		model.setNsPrefix("fs", "http://www.fuzzysets.org#");
		try {
			model.read(url, "RDF/XML");
		} catch (Exception e) {
			show_notification("Format Error", "The dataset is not in RDF/XML format");
		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public void load_ttl(String url) {
		model.removeAll();
		model.clearNsPrefixMap();
		model.setNsPrefix("fs", "http://www.fuzzysets.org#");
		try {
			model.read(url, "TTL");
		} catch (Exception e) {
			show_notification("Format Error", "The dataset is not in RDF/Turtle format");
		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public void autocompletion() {

		List<String> l = new ArrayList<String>();
		l.add("f:type");
		l.add("f:HIGH()");
		l.add("f:MEDIUM()");
		l.add("f:LOW()");
		l.add("fs:High");
		l.add("fs:Medium");
		l.add("fs:Low");
		l.add("rdf:type");
		l.add("f:AT_LEAST");
		l.add("f:AT_MOST");
		l.add("f:CLOSE_TO");
		l.add("f:MORE_OR_LESS");
		l.add("f:VERY");
		l.add("f:AND_PROD");
		l.add("f:OR_PROD");
		l.add("f:AND_LUK");
		l.add("f:OR_LUK");
		l.add("f:AND_GOD");
		l.add("f:OR_GOD");
		l.add("f:MEAN");
		l.add("f:WSUM");
		l.add("f:WMAX");
		l.add("f:WMIN");
		l.add("f:FSUM");
		l.add("f:FMAX");
		l.add("f:FMIN");
		l.add("f:FCOUNT");
		l.add("f:PCOUNT");
		l.add("f:QUANT");
		l.add("f:FAVG");
		l.add("f:WEIGHT");
		l.add("SELECT");
		l.add("WHERE");
		l.add("FILTER");
		l.add("HAVING");
		l.add("BIND");
		l.add("ORDER BY");
		l.add("VALUES");
		l.add("LET");
		editor.setCustomAutocompletion(l);

	}

	public void show_rdf() {

		List<String> l = new ArrayList<String>();
		l.add("f:type");
		l.add("f:HIGH()");
		l.add("f:MEDIUM()");
		l.add("f:LOW()");
		l.add("fs:High");
		l.add("fs:Medium");
		l.add("fs:Low");
		l.add("rdf:type");
		l.add("f:AT_LEAST");
		l.add("f:AT_MOST");
		l.add("f:CLOSE_TO");
		l.add("f:MORE_OR_LESS");
		l.add("f:VERY");
		l.add("f:AND_PROD");
		l.add("f:OR_PROD");
		l.add("f:AND_LUK");
		l.add("f:OR_LUK");
		l.add("f:AND_GOD");
		l.add("f:OR_GOD");
		l.add("f:MEAN");
		l.add("f:WSUM");
		l.add("f:WMAX");
		l.add("f:WMIN");
		l.add("f:FSUM");
		l.add("f:FMAX");
		l.add("f:FMIN");
		l.add("f:FCOUNT");
		l.add("f:PCOUNT");
		l.add("f:QUANT");
		l.add("f:FAVG");
		l.add("f:WEIGHT");
		l.add("SELECT");
		l.add("WHERE");
		l.add("FILTER");
		l.add("HAVING");
		l.add("BIND");
		l.add("ORDER BY");
		l.add("VALUES");
		l.add("LET");

		Map<String, String> prefix = model.getNsPrefixMap();

		String query_load = "";

		for (String p : prefix.keySet()) {
			if (!p.equals("")) {
				query_load = query_load + "PREFIX " + p + ": " + "<" + prefix.get(p) + ">\r\n";
			}
		}

		query_load = query_load + "PREFIX f: <http://www.fuzzy.org#>\r\n" + "PREFIX json: <http://www.json.org#>\r\n";

		query_load = query_load + "SELECT ?s ?p ?o\r\n WHERE\r\n {\r\n ?s ?p ?o\r\n }\r\n";

		editor.setValue(query_load);

		// PONER LOS CASOS DEGENERADOS!!!

		String fuzzification = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n" + "PREFIX json: <http://www.json.org#>\r\n"
				+ "PREFIX f: <http://www.fuzzy.org#>\r\n" + "SELECT ?Subject ?Property ?Object ?Low ?Medium ?High "
				+ "WHERE { ?Subject ?Property ?Object . "
				+ "{SELECT ?Property (MIN(xsd:float(?Object)) AS ?Min) (MAX(xsd:float(?Object)) AS ?Max) (AVG(xsd:float(?Object)) AS ?Avg)  "
				+ "WHERE { ?Subject ?Property ?Object . \n"
				+ "FILTER(!STRSTARTS(STR(?Subject), 'http://www.fuzzy.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Property), 'http://www.fuzzy.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Object), 'http://www.fuzzy.org'))"
				+ "FILTER(!STRSTARTS(STR(?Subject), 'http://www.w3.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Property), 'http://www.w3.org')) ."
				// + "FILTER(!STRSTARTS(STR(?Object), 'http://www.w3.org'))"
				+ " }" + " GROUP BY ?Property} ."
				+ "BIND(if(xsd:float(?Object)<=?Avg,(xsd:float(?Object)-?Min)/(?Avg-?Min),(?Max-xsd:float(?Object))/(?Max-?Avg)) AS ?Medium) ."
				+ "BIND((?Max-xsd:float(?Object))/(?Max-?Min) AS ?Low) ."
				+ "BIND((xsd:float(?Object)-?Min)/(?Max-?Min) AS ?High) ." + "}" + "ORDER BY ?Property";

		String fuzzy = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n" + "PREFIX f: <http://www.fuzzy.org#>\r\n"
				+ "PREFIX json: <http://www.json.org#>\r\n"
				+ "SELECT ?Individual ?Property ?TruthDegree ?FuzzySet ?Element  WHERE { {" + "?Individual f:type ?s . "
				+ "?s rdf:type ?FuzzySet . " + "?s f:onProperty ?Property . " + "?s f:truth ?TruthDegree ."
				+ "FILTER(!STRSTARTS(STR(?FuzzySet), 'http://www.w3.org')) } "
				+ "UNION {?Individual ?Property ?bn . ?bn f:item ?Element . ?bn f:truth ?TruthDegree }"

				+ "} " + "ORDER BY ?Individual";

		String crisp = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n" + "PREFIX f: <http://www.fuzzy.org#>\r\n"
				+ "PREFIX json: <http://www.json.org#>\r\n"
				+ "SELECT ?Subject ?Property ?Object WHERE { ?Subject ?Property ?Object . \n"
				+ "FILTER(!STRSTARTS(STR(?Subject), 'http://www.fuzzy.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Property), 'http://www.fuzzy.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Object), 'http://www.fuzzy.org'))"
				+ "FILTER(!STRSTARTS(STR(?Subject), 'http://www.w3.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Property), 'http://www.w3.org')) ."
				+ "FILTER(!STRSTARTS(STR(?Object), 'http://www.w3.org'))" + " }" + " ORDER BY ?Property";

		Query query_test = QueryFactory.create(fuzzy);
		ResultSet result_test = (ResultSet) QueryExecutionFactory.create(query_test, model).execSelect();

		if (result_test.hasNext()) {
		} else {
			Query query_fuzzification = QueryFactory.create(fuzzification);
			ResultSet result_fuzzification = (ResultSet) QueryExecutionFactory.create(query_fuzzification, model)
					.execSelect();
			while (result_fuzzification.hasNext()) {
				QuerySolution solution = result_fuzzification.next();
				if (solution.getLiteral("Low") == null & solution.getLiteral("Medium") == null
						& solution.getLiteral("High") == null) {
				} else {

					model.add(solution.getResource("Subject"),
							ResourceFactory.createProperty("http://www.fuzzy.org#type"),
							ResourceFactory.createResource(solution.getResource("Subject") + "Low"
									+ solution.getResource("Property").getLocalName()));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Low"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.fuzzysets.org#Low"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Low"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#onProperty"),
							solution.getResource("Property"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Low"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#truth"), solution.getLiteral("Low"));
					model.add(solution.getResource("Subject"),
							ResourceFactory.createProperty("http://www.fuzzy.org#type"),
							ResourceFactory.createResource(solution.getResource("Subject") + "Medium"
									+ solution.getResource("Property").getLocalName()));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Medium"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.fuzzysets.org#Medium"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Medium"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#onProperty"),
							solution.getResource("Property"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "Medium"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#truth"),
							solution.getLiteral("Medium"));
					model.add(solution.getResource("Subject"),
							ResourceFactory.createProperty("http://www.fuzzy.org#type"),
							ResourceFactory.createResource(solution.getResource("Subject") + "High"
									+ solution.getResource("Property").getLocalName()));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "High"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.fuzzysets.org#High"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "High"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#onProperty"),
							solution.getResource("Property"));
					model.add(
							ResourceFactory.createResource(solution.getResource("Subject") + "High"
									+ solution.getResource("Property").getLocalName()),
							ResourceFactory.createProperty("http://www.fuzzy.org#truth"), solution.getLiteral("High"));
				}

			}

		}

		List<HashMap<String, RDFNode>> rows_dataset_fuzzy = new ArrayList<>();
		List<HashMap<String, RDFNode>> rows_dataset_crisp = new ArrayList<>();

		Query query_fuzzy = QueryFactory.create(fuzzy);
		Query query_crisp = QueryFactory.create(crisp);
		ResultSet result_fuzzy = (ResultSet) QueryExecutionFactory.create(query_fuzzy, model).execSelect();
		ResultSet result_crisp = (ResultSet) QueryExecutionFactory.create(query_crisp, model).execSelect();
		dataset_fuzzy.removeAllColumns();
		dataset_crisp.removeAllColumns();
		List<String> variables_fuzzy = result_fuzzy.getResultVars();
		List<String> variables_crisp = result_crisp.getResultVars();
		rows_dataset_fuzzy.clear();
		rows_dataset_crisp.clear();
		while (result_fuzzy.hasNext()) {
			QuerySolution solution = result_fuzzy.next();
			LinkedHashMap<String, RDFNode> sol = new LinkedHashMap<String, RDFNode>();
			for (String vari : variables_fuzzy) {

				if (solution.get(vari) == null) {
					sol.put(vari, ResourceFactory.createResource(" "));
				} else {
					sol.put(vari, solution.get(vari));
					if (solution.get(vari).isURIResource()) {
						if (model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) == null) {
							l.add(solution.get(vari).asNode().getLocalName());
						} else {
							l.add(model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) + ":"
									+ solution.get(vari).asNode().getLocalName());
						}
					}
				}

			}
			rows_dataset_fuzzy.add(sol);
		}

		while (result_crisp.hasNext()) {
			QuerySolution solution = result_crisp.next();
			LinkedHashMap<String, RDFNode> sol = new LinkedHashMap<String, RDFNode>();
			for (String vari : variables_crisp) {
				sol.put(vari, solution.get(vari));
				if (solution.get(vari).isURIResource()) {
					if (model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) == null) {
						l.add(solution.get(vari).asNode().getLocalName());
					} else {
						l.add(model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) + ":"
								+ solution.get(vari).asNode().getLocalName());
					}
				}

			}
			rows_dataset_crisp.add(sol);
		}

		if (rows_dataset_fuzzy.size() > 0) {
			ldataset_fuzzy.setVisible(true);
			dataset_fuzzy.setVisible(true);

			HashMap<String, RDFNode> sr = rows_dataset_fuzzy.get(0);
			for (Map.Entry<String, RDFNode> entry : sr.entrySet()) {
				dataset_fuzzy.addColumn(h -> h.get(entry.getKey())).setHeader(entry.getKey()).setAutoWidth(true)
						.setResizable(true).setSortable(true)
						.setComparator((x, y) -> isNumeric(x.get(entry.getKey()).toString())
								& isNumeric(y.get(entry.getKey()).toString())
										? Float.compare(Float.parseFloat(x.get(entry.getKey()).toString()),
												Float.parseFloat(y.get(entry.getKey()).toString()))
										: x.get(entry.getKey()).toString().compareTo(y.get(entry.getKey()).toString()));

			}
			// show_notification("Successful!", "Fuzzy dataset has been downloaded!");
		} else {
			show_notification("Download!", "This fuzzy dataset is empty!");
		}
		dataset_fuzzy.setItems(rows_dataset_fuzzy);

		if (rows_dataset_crisp.size() > 0) {
			ldataset_crisp.setVisible(true);
			dataset_crisp.setVisible(true);

			HashMap<String, RDFNode> sr = rows_dataset_crisp.get(0);
			for (Map.Entry<String, RDFNode> entry : sr.entrySet()) {
				dataset_crisp.addColumn(h -> h.get(entry.getKey())).setHeader(entry.getKey()).setAutoWidth(true)
						.setResizable(true).setSortable(true)
						.setComparator((x, y) -> isNumeric(x.get(entry.getKey()).toString())
								& isNumeric(y.get(entry.getKey()).toString())
										? Float.compare(Float.parseFloat(x.get(entry.getKey()).toString()),
												Float.parseFloat(y.get(entry.getKey()).toString()))
										: x.get(entry.getKey()).toString().compareTo(y.get(entry.getKey()).toString()));
			}
			// show_notification("Successful!", "Crisp dataset has been downloaded!");
		} else {
			show_notification("Downloaded!", "This crisp dataset is empty!");
		}
		dataset_crisp.setItems(rows_dataset_crisp);

		ldataset_fuzzy.add(dataset_fuzzy);
		ldataset_crisp.add(dataset_crisp);
		editor.setCustomAutocompletion(l);

		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		String content = "";
		String file2 = "C:/tmp-sparql/" + "model.rdf";
		Path path = Paths.get(file2);

		try {
			content = Files.readString(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		editorO.setValue(content);

	}

	public void load_tree(JsonValue l, String root) {

		if (l.isNumber()) {
		} else if (l.isBoolean()) {
		} else if (l.isString()) {
		} else if (l.isObject()) {

			for (Entry<String, JsonValue> e : l.getAsObject().entrySet()) {

				model.add(ResourceFactory.createResource("http://www.json.org#" + root),
						ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
						ResourceFactory.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));

				if (e.getValue().isPrimitive()) {

					if (e.getValue().toString().equals(" ")) {
					} else {
						model.add(ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
								ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
								ResourceFactory.createResource("http://www.w3.org/2002/07/owl#DataProperty"));

						model.add(ResourceFactory.createResource("http://www.json.org#" + root),
								ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
								ResourceFactory.createPlainLiteral(e.getValue().toString().replace("\"", "")));

					}

				} else

				{
					model.add(ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.w3.org/2002/07/owl#ObjectProperty"));

					model.add(ResourceFactory.createResource("http://www.json.org#" + root),
							ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
							ResourceFactory.createResource("http://www.json.org#" + root + e.getKey()));

					load_tree(e.getValue(), root + e.getKey());
				}

			}
		}

		else if (l.isArray()) {
			JsonArray children = l.getAsArray();

			for (JsonValue e : children) {
				if (root == "") {
					load_tree(e, root + e.hashCode());
					model.add(ResourceFactory.createResource("http://www.json.org#" + root + e.hashCode()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.json.org#item"));

				} else
					load_tree(e, root);

			}
		}

	}

	public void load_json(String url) {

		InputStream input;
		try {
			input = new URL(url).openStream();
			JsonValue e = null;
			try {
				e = JSON.parseAny(input);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				show_notification("Format Error", "The dataset is not in JSON format");

			}

			model.removeAll();
			model.clearNsPrefixMap();
			model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
			// model.setNsPrefix("json", "http://www.json.org#");
			model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			// model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
			model.setNsPrefix("f", "http://www.fuzzy.org#");
			model.setNsPrefix("fs", "http://www.fuzzysets.org#");

			load_tree(e, "");

		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

	}

	public static boolean isNumeric(String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			Float.parseFloat(strNum);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

}
