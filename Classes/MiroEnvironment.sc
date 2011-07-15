MiroEnvironment : Environment {
	classvar <environments, meTitles;
	var <initialized=false, <prepared=false, <playing=false;
	var <name, <resources, resourceClasses, <cleanupCondition, <playNextCondition, <prepareNextCondition, <preparedCondition=0;

	*initClass {
		environments = IdentityDictionary.new;
		meTitles = IdentityDictionary.new;
	}
	
	*get {
		| key |
		var newTmp;
		^environments.atFail( key, { environments.put( key, newTmp=MiroEnvironment.new( key )); newTmp })
	}
	
	*gui {
		var window, list, initButton, prepareButton, playButton, freeButton,
			yPos=5, xPos=5, names;
		window = SCWindow.new( "MiroEnvironments", Rect(0,0,290,100), resizable:false, scroll:true );
		names = [
			[ \sinc1_campanas ],
			[ \flujo1_viento ],
			[ \flujo1_viento_secondhalf ],
			[ \raps1 ],
			[ \sinc2_geisers ],
			[ \flujo2_tierra ],
			[ \raps2 ],
			[ \sinc3_aloalo ],
			[ \flujo3_agua, \flujo3_vientos ],
			[ \raps3 ],
			[ \sinc4_canon ],
			[ \flujo4_voces ],
			[ \sinc5_respiraciones ],
			[ \flujo5_vientos, \flujo5_agua, \flujo5_tierra ],
		//	[ \raps4 ],
		//	[ \ghost_raps ],
			[ \sinc6_golem ],
			[ \coda_elementos ]
		];
		names.flatten.do({
			| key |
			var value = environments[ key ];
			meTitles[key] = SCStaticText( window, Rect( 5, yPos, 280-10, 20 ))
				.stringColor_( Color.grey )
				.string_( key );
			yPos = yPos+18; 
			SCButton( window, Rect( xPos, yPos, 60, 20))
				.states_([["init", Color.black, Color.grey(0.4)]])
				.action_({ value.initialize() });
			xPos = xPos + 65;
			SCButton( window, Rect( xPos, yPos, 60, 20))
				.states_([["prep", Color.yellow(0.6), Color.grey(0.4)]])
				.action_({ value.prepare() });
			xPos = xPos + 65;
			SCButton( window, Rect( xPos, yPos, 60, 20))
				.states_([["play", Color.green(0.6), Color.grey(0.4)]])
				.action_({ value.play() });
			xPos = xPos + 65;
			SCButton( window, Rect( xPos, yPos, 60, 20))
				.states_([["free", Color.red(0.6), Color.grey(0.4)]])
				.action_({ value.free() });
			yPos = yPos+26; xPos = 5; 
		});
		window.bounds_( window.bounds.height = yPos.min(500) );
		window.front;
		
		{
			"stargind updated".postln;
			
			while({ window.isClosed.not }, {
				names.flatten.do({
					|key|
					var value = environments[key];
					meTitles[key].stringColor = case
						{ value.isNil } { Color.grey }
						{ value.playing } { Color.green(0.9) }
						{ value.prepared } { Color.yellow(0.9) }
						{ value.initialized } { Color.black }
						{ true } { Color.grey }
				});
				0.5.wait;	
			})
		}.fork(AppClock);
	}
	
	*new {
		| name |
		^super.new.init( name )
	}
	
	init {
		| n |
		name = n;
		this.class.environments[ name ] = this;
		resources = Dictionary.new;	
		resourceClasses = [ Bus, CtkBus, Node, CtkNode, Task, NodeProxy ];
		this.put( \this, this );
	}
	
	put {
		| key, value |
		^super.put( key, value );
	}
	
	putGet {
		| key, value |
		^super.putGet( key, value );
	}
	
	gatherResources { 
//		var primary = false;
//		resources = Dictionary.new;
//		this.keysValuesDo({ 
//			| key, value |
//			resourceClasses.do({
//				| class |
//				if( value.isKindOf( class ) && primary.not, {
//					resources.put( key, value);
//					primary = true;
//					//break
//				})
//			});
//			if( primary.not, {
//				value.slotValuesDo({
//					| slot, value |
//					resourceClasses.do({
//						| class |
//						if( value.isKindOf( class ) && primary.not, {
//							resources.put(key ++ "." ++ slot, value);
//							primary = true;
//							//break;
//						})
//					});
//					
//				})
//			})
//		})
	}
	
	initialize {
		if( this[ \init ].notNil, {
			this.use({ ~init.value() });
			this.gatherResources();
		});
		initialized = true;
	}
	
	prepare {
		if( prepared.not, {
			prepared = true;	
			preparedCondition = Condition(false);
			if( this[ \prepare ].notNil, {
				this.use({ ~prepare.value() });
				this.gatherResources();
			});
			preparedCondition.test_(true).signal;
		})
	}
	
	play {
		| duration=0 |		
		playing = true;
		if( this[ \play ].notNil, {
			this.use({ ~play.value() });
		});
		^[ prepareNextCondition = Condition(false), 
			playNextCondition = Condition(false), 
			cleanupCondition = Condition(false) ]
	}
		
	free {
		if( this[ \free ].notNil, {
			this.use({ ~free.value() });
			resources.keysValuesDo({
				| key, val |
				if( val.isAlive, {
					format( "Resource at %.% might still be alive!", name, key ).warn 
				})
			})
		});
		playing = false;
		prepared = false;
		preparedCondition = 0;
	}
	
	prepareNext {
		prepareNextCondition.test_(true).signal;
	}
	
	playNext {
		playNextCondition.test_(true).signal;
	}
	
	cleanup {
		cleanupCondition.test_(true).signal;
	}
}

+Bus {
	isAlive { ^true }
	inspect { if( numChannels==1, { this.debugScope }, { this.scope } ) }
}

+CtkAudio {
	isAlive { ^this.isPlaying }
	asBus { ^Bus.new( if(this.class==CtkControl, {\control}, {\audio}), this.bus, this.numChans, this.server ) }
	inspect { this.asBus.inspect }
}

+Buffer {
	isAlive { ^true }
	inspect { this.plot }
}

+CtkBuffer {
	isAlive { ^this.isPlaying }
	asBuffer { ^Buffer.new( server, numFrames, numChannels, bufnum ) }
	inspect { this.asBuffer.plot }
}

+Node {
	isAlive { ^this.isRunning }
	inspect { this.query; this.trace; }
}

+CtkNode {
	isAlive { ^this.isPlaying }
	asNode { ^Node.basicNew( server, node ) }
	inspect { this.asNode.inspect }
}

+Task {
	isAlive {^this.isPlaying}
// 	inspect {} 
}

+NodeProxy {
	isAlive { ^this.isPlaying }
	inspect { this.scope; Node.basicNew( this.server, this.asNodeID ).trace;  }
}