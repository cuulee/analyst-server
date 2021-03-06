var Analyst = Analyst || {};

(function(A, $) {

	A.app = {};

	A.app.instance = new Backbone.Marionette.Application();

	A.app.instance.addRegions({
		appRegion: "#app"
	});

	A.app.instance.addInitializer(function(options){
		A.app.allProjects = new A.models.Projects();
		A.app.allProjects.fetch();

		A.app.user = new A.models.User({id:"self"});

		// don't start the app until the user has loaded, because the user is needed to assess
		// permissions. See issue #56.
		A.app.user.fetch()
		.fail(function (err) {
			if (err.status == 401)
				window.location.href='/login';
		})
		.always(function () {
			A.app.controller =  new A.app.AppController();

			moment.locale(A.app.user.get('lang'));

			if (A.map !== undefined)
				A.map.attributionControl.setPrefix(A.app.user.get("analystVersion"));

			A.app.projects  = new A.models.Projects();
			A.app.projects.fetch({reset: true, success: A.app.controller.initProjects});

			A.app.appRouter = new A.app.Router({controller: A.app.controller});

			if( ! Backbone.History.started) Backbone.history.start();

		});

		// any time we get a 401, redirect to the login page
		$(document).ajaxError(function (ev, err) {
			if (err.status == 401) {
				window.location.href = '/login';
			}
		});

	});

	A.app.Router = Marionette.AppRouter.extend({
  		appRoutes: {
    			':project/:x/:y/:z(/:namespace/*subroute)': 'invokeSubRoute',
					'create-project': 'invokeSubRoute',
					'home': 'index',
					'': 'index'
		}
	});

	A.app.AppController = Marionette.Controller.extend({

		index: function(options) {
			A.app.main = new A.app.Main();

			A.app.instance.appRegion.show(A.app.main);
			A.app.main.showWelcome();
		},

		invokeSubRoute: function(project, x, y, z, namespace, subroute) {
		  this.setProject(project);

			if(!A.app.main) {
				A.app.main = new A.app.Main();

				A.app.instance.appRegion.show(A.app.main);
			}

			this.setMap(x, y, z);

			if(namespace)
				this.setTab(namespace, subroute);

		},

		setMap : function(x,y,z) {
			this.x = x;
			this.y = y;
			this.z = z;

			A.map.setView(new L.LatLng(y,x), z);

			this.updateNav(true);
		},

		initProjects : function() {
			A.app.controller.setProject();
		},

		setParams : function(params) {

			// todo: pack
			//this.params = _.reduce(_.pairs(params), function(memo, param) { memmemo + param[0] + ":" +  param[1] + "/"});
			//this.updateNav(true);
		},

		setProject : function(project) {

			this.params = "";

			if(project)
				A.app.selectedProject = project;

			if(!A.app.selectedProject)
				return;

			A.app.instance.vent.trigger("setSelectedProject");

			if(A.app.projects) {
				var p = A.app.projects.get(project);
				if(p) {
					this.setMap(p.get('defaultLon'), p.get('defaultLat'), p.get('defaultZoom'));
				}
			}

			this.updateNav(true);
		},

		setTab : function(tab) {

			this.params = "";

			this.selectedTab = tab;
			this.updateNav(true);

			A.app.instance.vent.trigger("setSelectedTab", this.selectedTab);

			A.app.main.closeSidePanel();

			if(this.selectedTab == "transport-data") {
				var transportDataPanel = new A.transportData.DataLayout();
				A.app.main.showSidePanel(transportDataPanel);
			}
			else if (this.selectedTab == "transport-scenarios") {
				var transportScenarioPanel = new A.scenarioData.DataLayout();
				A.app.main.showSidePanel(transportScenarioPanel);
			}
			else if(this.selectedTab == "spatial-data-pointsets") {
				var pointSetDataPanel = new A.spatialData.PointSetDataLayout();
				A.app.main.showSidePanel(pointSetDataPanel);
			}
			else if(this.selectedTab == "spatial-data-shapefiles") {
				var pointSetDataPanel = new A.spatialData.ShapefileDataLayout();
				A.app.main.showSidePanel(pointSetDataPanel);
			}
			else if(this.selectedTab == "analysis-single") {
				var analaysisPanel = new A.analysis.AnalysisSinglePointLayout();
				A.app.main.showSidePanel(analaysisPanel);
			}
			else if(this.selectedTab == "analysis-regional") {
				var analaysisPanel = new A.analysis.AnalysisMultiPointLayout();
				A.app.main.showSidePanel(analaysisPanel);
			}
			else if(this.selectedTab == "create-project") {
				var createProjectPanel = new A.project.ProjectCreateView();
				A.app.main.showSidePanel(createProjectPanel);
			}
			else if(this.selectedTab == "project-settings") {
				var createProjectPanel = new A.project.ProjectCreateView({id: A.app.selectedProject});
				A.app.main.showSidePanel(createProjectPanel);
			}
		},

		updateNav : function(replace) {

			if(A.app.selectedProject) {
				var baseUrl = '/' + A.app.selectedProject + '/' + this.x  + '/' + this.y  + '/' +  this.z;

				if(this.selectedTab)
					baseUrl = baseUrl + '/' + this.selectedTab + "/";

				if(this.params)
					baseUrl = baseUrl + '/' + this.params + "/";

				A.app.appRouter.navigate(baseUrl, {replace: replace});
			}
		}
	});


	A.app.Main = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-main'),

		regions: {
			appNav: 			"#app-nav",
			appSidePanel:       "#app-side-panel",
			appCenterPanel:     "#app-center-panel"
		},

		initialize : function() {
			var _this = this;

			A.app.instance.vent.on("closeSidePanel", function(){
				_this.closeSidePanel();
			});

		},

		onRender : function() {

			// Get rid of that pesky wrapping-div.
    			// Assumes 1 child element present in template.
    			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);

			A.app.nav = new A.app.Nav();

			this.appNav.show(A.app.nav);

		},

		showSidePanel : function (mainLayout) {
			this.$('#app-side-panel').show();
			this.appSidePanel.show(mainLayout);

		},

		closeSidePanel : function() {
			this.appSidePanel.close();
			this.$('#app-side-panel').hide();
		},

		showWelcome : function() {
			var welcomeLayout = new A.app.Welcome();

			this.$('#app-side-panel').show();
			this.appSidePanel.show(welcomeLayout);
		},

		onShow: function() {

			A.map = L.map(this.$("#app-map")[0], { loadingControl: true,  zoomControl: false }).setView([0, -80.00], 4);

			if (A.app.user !== undefined)
				A.map.attributionControl.setPrefix(A.app.user.get("analystVersion"));

			// this has no streets, etc. the streets are drawn on top.
			L.tileLayer('//{s}.tiles.mapbox.com/v3/conveyal.ec7d6341/{z}/{x}/{y}{scale}.png',
			  {
					scale: L.Browser.retina ? '@2x' : '',
					detectRetina: true,
					attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors,' +
						'<a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">Mapbox</a>',
					maxZoom: 18
				}).addTo(A.map);

			// add the streets overlay
			L.tileLayer('//{s}.tiles.mapbox.com/v3/conveyal.hp092m0g/{z}/{x}/{y}{scale}.png',
			{
				scale: L.Browser.retina ? '@2x' : '',
				detectRetina: true,
				zIndex: 10
			}).addTo(A.map);

			new L.control.scale({position: 'bottomleft', metric: true, imperial: true}).addTo(A.map);

			new L.control.zoom({position: 'bottomleft'}).addTo(A.map);

			A.map.on("moveend", function(evt) {

				if(A.map.getCenter().lng != -80 && A.map.getCenter().lat != 0)
					A.app.controller.setMap(A.map.getCenter().lng, A.map.getCenter().lat, A.map.getZoom());
			});
		}
	});

	A.app.Nav = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-nav'),

		regions : {
			projectDropdown: "#projectDropdown",
			userDropdown: "#userDropdown",
			userQuotaWarning: "#userQuotaWarning",
			customLogo: "#customLogo"
		},

		events : {
			'click #transportDataTab' : 'clickTransportData',
			'click #transportScenarioTab' : 'clickTransportScenarios',
			'click #spatialDataTabShapefilesListItem' : 'clickSpatialDataTabShapefilesListItem',
			'click #spatialDataTabPointSetsListItem' : 'clickSpatialDataTabPointSetsListItem',
			'click #analysisTabSinglePoint' : 'clickAnalysisTabSinglePoint',
			'click #analysisTabRegional' : 'clickAnalysisTabRegional',
			'click #projectSettingsTab' : 'clickProjectSettings',
			'click .selectLang': 'selectLang'
		},

		initialize : function() {

			var _this = this;
			A.app.instance.vent.on("setSelectedProject", function(){
			  _this.render();
			});

			A.app.instance.vent.on("setSelectedTab", function(tab){
			_this.activeTab = tab;
			_this.render();
			});
		},

		onRender : function() {

			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);

			var projectDropdownView = new A.app.ProjectListView({collection: A.app.projects});

			var userDropdownView = new A.app.UserMenuView({model: A.app.user});
			var userQuotaWarningView = new A.app.UserQuotaWarningView({model: A.app.user});
			var customLogoView = new A.app.CustomLogoView({model: A.app.user});

			this.projectDropdown.show(projectDropdownView);
			this.userDropdown.show(userDropdownView);
			this.userQuotaWarning.show(userQuotaWarningView);
			this.customLogo.show(customLogoView);
		},

		selectLang : function(evt) {
			evt.preventDefault();
			var lang = $(evt.target).data("lang");
			$.post("/setLang", {lang: lang}, function() {
				window.location.reload();
			});
		},

		clickTransportData : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("transport-data");
		},

		clickTransportScenarios : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("transport-scenarios");
		},

		clickSpatialDataTabShapefilesListItem : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("spatial-data-shapefiles");
		},

		clickSpatialDataTabPointSetsListItem : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("spatial-data-pointsets");
		},

		clickAnalysisTabSinglePoint : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("analysis-single");
		},

		clickAnalysisTabRegional : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("analysis-regional");
		},

		clickProjectSettings : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("project-settings");
		},

		templateHelpers: {
			selectedProject : function () {
				return A.app.selectedProject != null;
			},
			transportDataActive : function () {
				return this.activeTab == "transport-data"
			},
			transportScenariosActive: function () {
				return this.activeTab == "transport-scenarios";
			},
			spatialDataActive : function () {
				return this.activeTab == "spatial-data-shapefiles" || this.activeTab == "spatial-data-pointsets";
			},
			analysisActive : function () {
				return this.activeTab == "analysis-single" ||  this.activeTab == "analysis-regional";
			},
			settingsActive : function () {
				return this.activeTab == "project-settings";
			},
			admin: function () {
				return A.app.user.get('admin') === true;
			}
		}
	});

	A.app.UserMenuView = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('app', 'app-user-menu'),
		tagName: 'li',

		initialize: function () {

			this.model.on('change', this.render);
		},

		onRender : function() {
			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);
		}

	});

	A.app.UserQuotaWarningView = Backbone.Marionette.ItemView.extend({
		template: Handlebars.getTemplate('app', 'app-user-quota-warning'),
		tagName: 'li',

		initialize: function () {
			this.model.on('change', this.render);
		}
	});

	/** Show the custom logo if present */
	A.app.CustomLogoView = Backbone.Marionette.ItemView.extend({
		template: Handlebars.getTemplate('app', 'app-custom-logo-view'),
		tagName: 'span',

		initialize: function () {
			this.model.on('change', this.render);
		}
	});

	A.app.ProjectListItemView = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('project', 'project-list-item-template'),
		tagName: 'li',

		initialize: function () {
			this.model.on('change', this.render);
		}

	});

	A.app.ProjectListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('project', 'project-list-template'),
		itemView: A.app.ProjectListItemView,

		events : {
			'click .selectProject': 'selectProject',
			'click #createNewProject' : 'createNewProject'
		},

		initialize : function() {
		},

		templateHelpers: {
			selectedProject : function () {
				var selectedProject = this.collection.get(A.app.selectedProject);
				if(selectedProject != null)
					return selectedProject.get("name");
				else
					return false;
			}
		},

		onRender : function() {
			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);
		},

		selectProject : function(evt) {
			evt.preventDefault();
			var id = $(evt.target).data("id");
			A.app.controller.setProject(id);
		},

		createNewProject : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("create-project");
		},

		appendHtml: function(collectionView, itemView){
			collectionView.$("#projectList").prepend(itemView.el);
		}
	});

	A.app.SidePanel = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-side-panel'),

		regions: {
			main : '#main'
		},

		initialize : function (options) {

			this.mainLayout = options.mainLayout;
		},

		onShow : function() {

			this.main.show(this.mainLayout);



		}
	});



	A.app.Welcome = Marionette.Layout.extend({

		template: Handlebars.getTemplate('docs', 'docs-welcome', 'en'),

		events : {
			'click #startTour': 'startTour'
		},

		startTour : function(evt) {
			A.app.tour = new Tour({

			steps: [ {
					element: "#transportDataTab",
					title: "Build Transport Bundles",
					content: "Create a transport bundle by clicking \"Add\" above and uploading a GTFS feed, or create a GTFS feed using TransitMix or TransitWand."
				}, {
					element: "#spatialDataTab",
					title: "Manage Spatial Data",
					content: "Spatial data sets allow measurement of access to spatially distributed characteristics like population or jobs. Create one by clicking \"Add\" above and upload a Shapefile."
			}]});

			// Initialize the tour
			A.app.tour.init();

			// Start the tour
			A.app.tour.start(true);
		}

	});

	Handlebars.registerHelper('projectIdToName',
		function(id){
			if(A.app.allProjects.get(id))
				return A.app.allProjects.get(id).get("name");
			else
				return "Delete Project"
		}
	);

	Handlebars.registerHelper('writeAllowed',
	function(options){

		var permissions = _.find(A.app.user.get("projectPermissions"), function(val) {return val.projectId === A.app.selectedProject});

		if(permissions && permissions.write) {
			return options.fn(this);
		}

		return false;
	}
);

})(Analyst, jQuery);


$(document).ready(function() {

	Analyst.app.instance.start();

});
