ElementPanner {
	classvar <>server, vertPannerDef, perimeterPannerDef; 
	classvar <>behaviorsDict;

	var server, <controlNode, <fadeTime, <vpanner, <ppanner, elementBus;
	var <vertSynth, <perimSynth, <vertControlSynth, <perimControlSynth;
	var <currentPanner, <pannerDict, <pannerControlDict;
	
	*init {
		| server |
		this.server = server;
		behaviorsDict = IdentityDictionary.new;
		behaviorsDict[\perim] = IdentityDictionary.new;
		behaviorsDict[\rect] = IdentityDictionary.new;
		ElementPanner.sendSynths( server );
		ElementPanner.loadBehaviors();
	}

	*addBehavior {
		| region, name, func |
		behaviorsDict[ region ][ name ] = { SeedMaster.ugen(SeedMaster.seed); ^func.value() };
	}
	
	*addBehaviors {
		| region, behaviorDict |
		behaviorsDict[ region ].putAll( behaviorDict );
	}	
	
	*loadBehaviors {
		| filename |
		var data;
		filename = filename ? Platform.userAppSupportDir +/+ "extensions/mirogoj/data/elementBehaviors.txt";
		if( File.exists( filename ), {
			data = Object.readArchive( filename );
			behaviorsDict = data;
		})
	}
	
	*saveBehaviors {
		| filename |
		var existing;
		filename = filename ? Platform.userAppSupportDir +/+ "extensions/mirogoj/data/elementBehaviors.txt";
		if( File.exists( filename ), {
			existing = Object.readArchive( filename );
			existing.putAll( behaviorsDict );
			existing.writeArchive( filename );
		},{
			behaviorsDict.writeArchive( filename );
		})		
	}

	
	*sendSynths {
		| server |
		vertPannerDef = SynthDef( \vertPanner, {
			| audioIn, controlIn, on=1, onTime=5 |
//			var amp, x, y, width;
			var amp, direction, elevation, distance, ambiSig;
			var groups;
			var monoSig, leftMono, rightMono, left, right,
				la, lb, lc, ld, ra, rb, rc, rd, n,
				outChans;
			SeedMaster.ugen;
			on = Slew.kr( on.round(1), onTime.reciprocal, onTime.reciprocal );
			PauseSelf.kr( (on>0).not );
			
			
			// This is all specific to the tertulia speaker setup. 
/*
			// These are the parameters you're controlling, and using to pan the sound
			#amp, x, y, width = In.kr( controlIn, 4 );

			// The rest does the actual panning, 
			// but way more complex than needed for space lab
			x = x*2-1; y = (y*2-1)*(3/5);
			x = (x*(1-width)) + (0.5*width);
			width = (width*2)+2;
			
			monoSig = amp * In.ar( audioIn ) * on;
			#leftMono, rightMono = Pan2.ar( monoSig, x );
			#lc, ld, n, la, lb = PanAz.ar( 5, leftMono, y, 1, width, -0.5 );
			#rc, rd, n, ra, rb = PanAz.ar( 5, rightMono, y, 1, width, -0.5 );
			outChans = [ la, lb, lc, ld, ra, rb, rc, rd ];
		
			groups = [ 1, 3, 6, 10, 2, 4, 7, 11 ].collect({ 
					|g|
					VirtualSpeakerArray.get.groupings[g].collect({ |b| b.inBus.bus })
				});			
			groups.do({ 
				| group, i |
				Out.ar( group.postln, outChans[i] );
			});
*/
			
			// A basic non-Tertulia example.
			#amp, direction, elevation, distance = In.kr( controlIn, 4 );
			monoSig = In.ar( audioIn ) * on;
			ambiSig = BFEncode1.ar( monoSig, direction, elevation, distance, amp );
			Out.ar( 0, ambiSig ); // out to your ambisonic decoder
			
			// Now, just make sure you use \vertPanner.
		});
		
		perimeterPannerDef = SynthDef( \perimeterPanner, {
			| 	audioIn, controlIn, on=1, onTime=5 |
			var amp, pos, width;
			var groups;
			var monoSig,
				a, b, c, d, e, f, g, h, n,
				outChans, v;
/*			SeedMaster.ugen;
			on = Slew.kr( on.round(1), onTime.reciprocal, onTime.reciprocal );
			PauseSelf.kr( (on>0).not );
												
			#amp, pos, width = In.kr( controlIn, 3);
		
			pos = (pos*2-1)*(8/10);
			width = (width*3)+2;
			
			monoSig = amp * In.ar( audioIn ) * on;
			#e,f,g,h,n,n,a,b,c,d = PanAz.ar( 10, monoSig, pos, 1, width, -0.5 );
			outChans = [a,b,c,d,e,f,g,h];
		
			v = VirtualSpeakerArray.get;
			groups = [ v.groupings[0], v.groupings[1], v.groupings[2], 
					v.groupings[5]++v.groupings[4][0..1], v.groupings[8]++v.groupings[7][0..1],
					v.groupings[11], v.groupings[10], v.groupings[9]]
				.collect({ 
					|g|
					g.collect({ |b| b.inBus.bus })
				});			
			groups.do({ 
				| group, i |
				Out.ar( group.postln, outChans[i] );
			});
*/		});
		
		perimeterPannerDef.send( server ).store;
		vertPannerDef.send( server ).store;
	}
	
	*new {
		|eb |
		^super.new.init(eb)
	}
	
	init {
		|eb|
		server = this.class.server;
		elementBus = eb;

		vertControlSynth = NodeProxy.new( server, \control, 4 );
		vertControlSynth.parentGroup_(GroupManager.get(server).controls);
		vertControlSynth.fadeTime = 10;


		perimControlSynth = NodeProxy.new( server, \control, 3 );
		perimControlSynth.parentGroup_(GroupManager.get(server).controls);
		perimControlSynth.fadeTime = 10;		
				
		vertSynth = Synth.newPaused( \vertPanner, 
			[\audioIn, elementBus, \controlIn, vertControlSynth.index, \randomSeed, SeedMaster.seed],
			target:GroupManager.get(server).elementPanners );
		perimSynth = Synth.newPaused( \perimeterPanner, 
			[\audioIn, elementBus, \controlIn, perimControlSynth.index, \randomSeed, SeedMaster.seed],
			target:GroupManager.get(server).elementPanners );

		pannerDict = IdentityDictionary.new;
		pannerDict[\rect] = vertSynth;
		pannerDict[\perim] = perimSynth;		

		pannerControlDict = IdentityDictionary.new;
		pannerControlDict[\rect] = vertControlSynth;
		pannerControlDict[\perim] = perimControlSynth;
	}
	
	play {
		server.makeBundle( nil, {
			this.setRegion(\rect);
			vertControlSynth.source = [0,0,0,0];
			perimControlSynth.source = [0,0,0];
		})
	}
	
	free {
		server.makeBundle( nil, {
			vertControlSynth.clear;
			perimControlSynth.clear;
			vertSynth.free;
			perimSynth.free;
		})		
	}
	
	setRegion {
		| region, fadeTime=1 |
		var panner;
		if( region.notNil && region != currentPanner, {
			panner = pannerDict[region];
			server.makeBundle( nil, { 
				if( currentPanner.notNil, { pannerDict[currentPanner].set( \on, 0, \onTime, fadeTime ) });
				panner.run(true);
				panner.set( \on, 1, \onTime, fadeTime );
			});		
			currentPanner = region;
		})
	}
	
	setControls {
		| val, fadeTime=1 |
		pannerControlDict[ currentPanner ].fadeTime = fadeTime;
		pannerControlDict[currentPanner].source = val;
	}
	
	updateState {
		| fadeTime=1, region, source |
		region = region ? currentPanner;
		server.makeBundle( nil, {
			this.setRegion( region, fadeTime );
			source.notNil.if({ this.setControls( source, fadeTime ) });
		})
	}
	
	updateBehavior {
		| fadeTime=1, behavior, args |
		var region, controlSetting;
		if( (controlSetting = this.class.behaviorsDict[\rect][ behavior ]).notNil, {
			region = \rect;
		},{
			if( (controlSetting = this.class.behaviorsDict[\perim][ behavior ]).notNil, {
				region = \perim;
			},{
				"Behavior does not exist.".error;
				^this
			}) 
		});
		
		this.setRegion( region, fadeTime);
		pannerControlDict[currentPanner].fadeTime = fadeTime;
		if( controlSetting.class == Function && args.notNil, {
			pannerControlDict[currentPanner].source = 
				case
				{ args.isNumber } 
					{ { controlSetting.value( args ) } }
				{ args.class == Event} 
					{ { controlSetting.valueWithEnvir( args ) } }
				{ args.class == Array}
					{ { controlSetting.valueArray( args ) } };
		},{
			pannerControlDict[currentPanner].source = controlSetting 
		})
	}
}