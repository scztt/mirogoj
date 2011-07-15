VirtualSpeakerArrayUI {
	var <>window, backView, backImage, speakerIcons, listenerIcon, volumeControl, movementButton;
	var <>speakerArray;
	var speakersChanged=false, listenerChanged=false;
	
	*new {
		| speakerArray |
		var ui;
		^super.new.init( speakerArray )
	}
	
	free {
		speakerArray.removeDependant( this );
	}
	
	sx { | x | ^x/window.bounds.width*800 }
	sy { | y | ^y/window.bounds.height*800 }
	sxy { |point| ^ this.sx(point.x) @ this.sy(point.y) }

	isx { | x | ^x/800*window.bounds.width }
	isy { | y | ^y/800*window.bounds.height }
	isxy { |point| ^ this.isx(point.x) @ this.isy(point.y) }
	
	init {
		| speakerArray |
		var dragStart, dragEnd, backViewOldBounds;
		this.speakerArray = speakerArray;
		this.speakerArray.addDependant( this );
		backImage = SCImage.new( "~/Library/Application Support/SuperCollider/Extensions/mirogoj/miromap.jpg".standardizePath );
		
		window = SCWindow.new("Speaker array", Rect(50,50,600,600));
		window.view.backgroundImage_( backImage, 10, 0.2 );
		window.view.background_(Color.grey(0.7));
		window.onClose_({ this.free });		
		backViewOldBounds = Rect(0,0,0,0);
		backView = SCUserView( window, Rect(0,0,600,600))
			.drawFunc_({ 
				|view|
				if( (backViewOldBounds == view.bounds).not, { 
					backViewOldBounds = view.bounds;
					listenerIcon.bounds = Rect( 
								this.isx(speakerArray.listenerPos.x)-this.isx(20), 
								this.isy(speakerArray.listenerPos.y)-this.isy(20), 
								this.isx(40), 
								this.isy(40)
							);
					this.updateSpeakerPositions();
				})
			})
			.mouseDownAction_({
				| view, x, y |
				dragStart = this.sxy(x@y);
			})
			.mouseUpAction_({
				| view, x, y |
				dragEnd = this.sxy(x@y);
				speakerArray.listenerPos = dragStart;
				speakerArray.listenerAngle = 
					(-0.75 - ((dragEnd-dragStart).scale(1@1.neg).theta/(2*pi))).mod(1);
				listenerIcon.bounds = Rect( 
					this.isx(speakerArray.listenerPos.x)-this.isx(20), 
					this.isy(speakerArray.listenerPos.y)-this.isy(20), 
					this.isx(40), 
					this.isy(40) );
				// walk mode
				if( movementButton.value == 1, {
					speakerArray.walkTest([ dragStart, dragEnd ], 1);
				},{
					window.refresh();
				})
				
			})
			.keyDownAction_({
				| view, a, b, key |
				switch( key )
					{ 63232 }
					{ speakerArray.listenerPos = 
						speakerArray.listenerPos - (5@0)
							.rotate((speakerArray.listenerAngle+0.25)*pi*2) }
					{ 63233 }
					{ speakerArray.listenerPos = 
						speakerArray.listenerPos + (5@0)
							.rotate((speakerArray.listenerAngle+0.25)*pi*2) }
					{ 63234 }
					{ speakerArray.listenerAngle = (speakerArray.listenerAngle-0.05).mod(1) }
					{ 63235 } 
					{ speakerArray.listenerAngle = (speakerArray.listenerAngle+0.05).mod(1) };
			});
		backView.resize_(5);
		volumeControl = EZSlider( window, Rect(10,10,300,20), "amp", 
			ControlSpec( -20, 40, default:0, units:"dB"), action:{
				| slider |
				speakerArray.amp = slider.value.dbamp;
			}, unitWidth:20);
			
		movementButton = SCButton( window, Rect(310,10,160,20) )
			.states_([["Movement: instant", Color.black, Color.grey],
					["Movement: walk", Color.blue, Color.grey]])
			.action_({
				|view|
				if( view.value==0, {
					speakerArray.clearWalkTest();
				})
			});

		this.updateSpeakers();
		this.initListenerIcon();
		
		window.front;
	}
	
	initListenerIcon {
		listenerIcon = SCUserView( window, Rect( this.isx(speakerArray.listenerPos.x)-this.isx(20), this.isy(speakerArray.listenerPos.y)-this.isy(20), this.isx(40), this.isy(40) ))
			.canFocus_(false)
			.relativeOrigin_(true)
			.mouseDownAction_( backView.mouseDownAction )
			.mouseUpAction_( backView.mouseUpAction )
			.drawFunc_({
				| view |
				Pen.use({
					Pen.scale(this.isx(1), this.isy(1));
					Pen.translate( 20,20 );
					Pen.rotate( (speakerArray.listenerAngle*pi*2) );
					Pen.color = Color.green(0.7);
					Pen.fillOval( Rect(-13, -7, 26, 14) );
					Pen.color = Color.green(0.1, 0.6);
					Pen.fillOval( Rect(-5, -5, 10, 10) );
					Pen.color = Color.black;
					Pen.strokeOval( Rect(-13, -7, 26, 14) );
					Pen.line( 0@0, 0@(-8) );
					Pen.perform([\stroke]);
				})
			});
	}
	
	refresh {
		if( speakersChanged, { this.updateSpeakers });
		if( listenerChanged, { this.updateListener });
	}
	
	testSpeaker {
		| i |
		speakerArray.testSpeaker( i );
	}
	
	update {
		| who, what |
		switch ( what,
			\listener, { listenerChanged = true },
			\speakers, { speakersChanged = true }
		);
		{ this.refresh() }.defer();
	}
	
	updateSpeakers {
		var si;
		speakerIcons.do({
			| icon |
			icon.remove()
		});
		speakerIcons = speakerArray.speakers.collect({
			| speaker, i |
			var center, offset;
			center = speaker.x@speaker.y;
			offset = ( (((speaker.angle-0.25)*2*pi).cos*7)-10 ) @ ( (((speaker.angle-0.25)*2*pi).sin*7)-10 );
			center = this.isxy( center + offset );
			si = SCUserView( window, Rect( center.x,center.y, this.isx(20), this.isy(20)) )
				.relativeOrigin_(true)
				.mouseDownAction_({ this.testSpeaker(i) })
				.canFocus_(false)
				.drawFunc_({
					|view|
					Pen.use({
						Pen.scale( this.isx(1), this.isy(1) );
						Pen.translate( 10,10 );
						Pen.rotate( ((speaker.angle-0.25)*pi*2) );
						Pen.color = Color.blue(0.9, 0.5);
						Pen.addAnnularWedge( (-9)@0, 5, 16, -0.5, 1 );
						Pen.fill();
						Pen.color = Color.black(1,0.5);
						Pen.addAnnularWedge( (-9)@0, 5, 16, -0.5, 1 );
						Pen.stroke();
						Pen.fillRect( Rect(-3-7,-3,7,7));
					})
				});
			si;
		});
		speakersChanged = false;
	}
	
	updateSpeakerPositions {
		speakerIcons.do({
			|icon, i|
			var speaker, center, offset;
			speaker = speakerArray.speakers[i];
			center = speaker.x@speaker.y;
			offset = ( (((speaker.angle-0.25)*2*pi).cos*7)-10 ) @ ( (((speaker.angle-0.25)*2*pi).sin*7)-10 );
			center = this.isxy( center + offset );
			icon.bounds = Rect( center.x,center.y, this.isx(20), this.isy(20));
		})
	}
	
	updateListener {
		listenerIcon.bounds = 
			Rect( this.isx(speakerArray.listenerPos.x)-this.isx(20), this.isy(speakerArray.listenerPos.y)-this.isy(20), this.isx(40), this.isy(40) );
		window.refresh;
		listenerChanged = false;
	}
}
