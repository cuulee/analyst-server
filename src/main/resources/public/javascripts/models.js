var Analyst = Analyst || {};

(function(A, $) {

  A.models = {}

  A.models.Project = Backbone.Model.extend({
    urlRoot: '/api/project',

    defaults: {
      id: null,
      name: null,
      description: null,
      boundary: null,
      defaultLat: null,
      defaultLon: null,
      defaultZoom: null,
      defaultScenario: null
    },

    selectedProject: function(evt) {
      return id === A.app.selectedProject;
    }

  });

  A.models.Projects = Backbone.Collection.extend({
    type: 'Projects',
    model: A.models.Project,
    url: '/api/project',
    comparator: 'name'

  });

  A.models.Shapefile = Backbone.Model.extend({
    urlRoot: '/api/shapefile',

    defaults: {
      id: null,
      name: null,
      description: null,
      projectId: null,
      shapeAttributes: [],
      featureCount: null
    },

    /**
     * Get the columns that are numeric and thus can be used for analysis.
     */
    getNumericAttributes: function() {
      var attrs = _.filter(this.get('shapeAttributes'), function(a) {
        return a.numeric;
      })

      attrs.sort(function (a1, a2) {
        if (a1.name < a2.name) return -1;
        if (a1.name > a2.name) return 1;
        return 0;
      });

      return attrs;
    }
  });

  /** static function to get the human-readable, localized name of an attribute */
  A.models.Shapefile.attributeName = function(attr) {
    if (attr.name == attr.fieldName)
      return attr.name;
    else
      return window.Messages('analysis.attribute-name', attr.name, attr.fieldName);
  };

  A.models.Shapefiles = Backbone.Collection.extend({
    type: 'Shapefiles',
    model: A.models.Shapefile,
    url: '/api/shapefile',
    comparator: 'name'

  });

  A.models.Bundle = Backbone.Model.extend({
    urlRoot: '/api/bundle',

    defaults: {
      id: null,
      name: null,
      description: null,
      filenames: [],
      status: null
    }
  });

  A.models.Bundles = Backbone.Collection.extend({
    type: 'Bundles',
    model: A.models.Bundle,
    url: '/api/bundle'
  });

  A.models.Scenario = Backbone.Model.extend({
    urlRoot: '/api/scenario',

    defaults: {
      id: null,
      name: null,
      description: null,
      bannedRoutes: null
    }
  });

  A.models.Scenarios = Backbone.Collection.extend({
    type: 'Scenarios',
    model: A.models.Scenario,
    url: '/api/scenario'
  });

  A.models.Query = Backbone.Model.extend({
    urlRoot: '/api/query',

    defaults: {
      id: null,
      name: null,
      mode: null,
      originShapefileId: null,
      destinationShapefileId: null,
      attributeName: null,
      scenarioId: null,
      status: null,
      totalPoints: null,
      completePoints: null,
      complete: false,
      envelope: null
    },

    updateStatus: function() {

    },

    getPoints: function() {

    }

  });

  A.models.Queries = Backbone.Collection.extend({
    type: 'Queries',
    model: A.models.Query,
    url: '/api/query',
    comparator: 'name'
  });

  A.models.User = Backbone.Model.extend({
    urlRoot: '/api/user/',

    defaults: {
      id: null,
      name: null,
      email: null,
      lang: null,
      projectPermissions: null
    },

    toJSON: function() {
      return _.extend({
        nearingQuota: this.get('quota') < 100000
      }, this.attributes);
    }
  });

  A.models.Users = Backbone.Collection.extend({
    type: 'Users',
    model: A.models.User,
    url: '/api/user'
  });

  A.models.LedgerEntry = Backbone.Model.extend({
    urlRoot: '/api/ledger',

    defaults: {
      id: null,
      userId: null,
      groupId: null,
      delta: 0,
      query: null,
      time: 0,
      parentId: null,
      reason: null,
      note: null
    }
  });

  A.models.Ledger = Backbone.Collection.extend({
    type: 'Ledger',
    url: '/api/ledger',
    model: A.models.LedgerEntry
  });

})(Analyst, jQuery);
